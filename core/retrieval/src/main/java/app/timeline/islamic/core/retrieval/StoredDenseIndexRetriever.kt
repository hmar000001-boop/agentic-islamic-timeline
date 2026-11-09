package app.timeline.islamic.core.retrieval

import app.timeline.islamic.core.knowledge.DenseEmbeddingIndexStore
import app.timeline.islamic.core.knowledge.EvidenceEligibility
import app.timeline.islamic.core.knowledge.EvidencePassage
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import app.timeline.islamic.core.knowledge.LocalTextEmbeddingModel
import app.timeline.islamic.core.knowledge.RetrievalFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.PriorityQueue

/** Resolves vector keys to immutable source evidence from the same installed pack database. */
interface DenseEvidenceLookup {
    /** Small, key-only eligibility query used while scanning; must not materialize passage text. */
    suspend fun readEligibleEvidenceKeysByStableKeys(
        pack: InstalledCorpusPackRecord,
        indexFile: File,
        stableEvidenceKeys: List<String>,
        filter: RetrievalFilter
    ): Set<String>

    /** Loads immutable original passages only for the bounded final top-K result set. */
    suspend fun readEvidenceByStableKeys(
        pack: InstalledCorpusPackRecord,
        indexFile: File,
        stableEvidenceKeys: List<String>,
        filter: RetrievalFilter
    ): List<EvidencePassage>
}

/**
 * Full-scan dense retrieval over SQLCipher-backed int8 vectors. Every vector is visited in stable
 * key pages (2,048 rows by default); only the best `limit` hits are retained in memory per pack.
 * Ranking is cosine similarity over the stored quantized vectors, not ANN approximation.
 */
class StoredDenseIndexRetriever(
    private val store: DenseEmbeddingIndexStore,
    private val model: LocalTextEmbeddingModel,
    private val evidenceLookup: DenseEvidenceLookup,
    private val pageSize: Int = DEFAULT_PAGE_SIZE
) : LocalDenseRetriever {

    init {
        require(pageSize in 1..MAX_PAGE_SIZE)
    }

    private data class Candidate(val score: Double, val stableEvidenceKey: String)

    override suspend fun search(
        installedPacks: List<Pair<InstalledCorpusPackRecord, File>>,
        normalizedQuery: String,
        filter: RetrievalFilter,
        limit: Int
    ): List<DenseRankedEvidence> {
        require(normalizedQuery.isNotBlank())
        require(limit in 1..500)
        val descriptor = model.descriptor
        val queryVector = model.embedQuery(normalizedQuery)
        require(queryVector.size == descriptor.dimension) {
            "Query embedding dimension differs from the installed model profile"
        }
        // Normalize once before the exhaustive scan; do not recompute a 768-D query norm for
        // every row in the vector index.
        val normalizedQueryVector = QuantizedEmbeddingCodec.normalizeQuery(queryVector)

        val bestFirst = compareByDescending<Candidate> { it.score }
            .thenBy { it.stableEvidenceKey }
        val worstFirst = bestFirst.reversed()
        val output = ArrayList<DenseRankedEvidence>()

        installedPacks.sortedBy { it.first.stableId }.forEach { (pack, indexFile) ->
            currentCoroutineContext().ensureActive()
            if (pack.status != InstalledCorpusPackRecord.STATUS_INSTALLED || !indexFile.isFile) return@forEach

            // An index built for another manifest, model artifact, task prefix or normalization
            // policy is not a degraded match: it is not eligible for use at all.
            val generation = store.activeGeneration(pack, descriptor) ?: return@forEach
            check(generation.state == ACTIVE_STATE) { "Dense generation is not active" }
            check(generation.dimension == descriptor.dimension) { "Dense generation dimension mismatch" }
            check(generation.corpusManifestSha256 == pack.manifestSha256) { "Dense generation belongs to another corpus manifest" }
            check(generation.embeddingProfileSha256 == descriptor.embeddingProfileSha256) {
                "Dense generation belongs to another embedding profile"
            }
            check(generation.modelSha256 == descriptor.artifactSha256) { "Dense generation belongs to another model artifact" }
            check(generation.vectorCount > 0L && generation.encodedBytes == generation.vectorCount * generation.dimension.toLong()) {
                "Dense generation metadata is inconsistent"
            }

            val queue = PriorityQueue(worstFirst)
            var afterKey = ""
            var scanned = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val page = store.vectorsPage(generation.generationId, afterKey, pageSize)
                if (page.isEmpty()) break
                check(page.size <= pageSize) { "Dense store exceeded the requested page size" }
                check(page.zipWithNext().all { (left, right) -> left.stableEvidenceKey < right.stableEvidenceKey }) {
                    "Dense store returned unstable or duplicate page ordering"
                }
                check(page.first().stableEvidenceKey > afterKey) { "Dense pagination cursor did not advance" }

                val eligibleKeys = evidenceLookup.readEligibleEvidenceKeysByStableKeys(
                    pack, indexFile, page.map { it.stableEvidenceKey }, filter
                )
                check(eligibleKeys.size <= page.size && eligibleKeys.all { key -> page.any { it.stableEvidenceKey == key } }) {
                    "Corpus pack returned an unknown or duplicate dense evidence identity"
                }

                page.forEach rowLoop@ { row ->
                    if (row.stableEvidenceKey !in eligibleKeys) return@rowLoop
                    val candidate = Candidate(QuantizedEmbeddingCodec.cosineWithNormalizedQuery(row.vector, normalizedQueryVector), row.stableEvidenceKey)
                    if (queue.size < limit) {
                        queue.add(candidate)
                    } else if (bestFirst.compare(candidate, queue.peek()) < 0) {
                        queue.remove()
                        queue.add(candidate)
                    }
                }

                scanned += page.size
                afterKey = page.last().stableEvidenceKey
            }
            check(scanned == generation.vectorCount) {
                "Dense generation vector count does not match the complete paginated scan"
            }

            val rankedKeys = queue.toList().sortedWith(bestFirst)
            if (rankedKeys.isNotEmpty()) {
                val hydrated = evidenceLookup.readEvidenceByStableKeys(
                    pack, indexFile, rankedKeys.map { it.stableEvidenceKey }, filter
                )
                val evidenceByKey = hydrated.associateBy { it.stableEvidenceKey }
                check(evidenceByKey.size == hydrated.size && evidenceByKey.keys == rankedKeys.map { it.stableEvidenceKey }.toSet()) {
                    "Top dense evidence could not be resolved uniquely from the installed corpus pack"
                }
                val packDigest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(pack.stableId.toByteArray(Charsets.UTF_8))
                    .take(6).joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val channel = "${descriptor.modelId.take(20)}:${descriptor.embeddingProfileSha256.take(8)}:${pack.packId.take(24)}:$packDigest"
                check(channel.length <= 128) { "Dense retrieval channel identity exceeds the orchestrator limit" }
                rankedKeys.forEachIndexed { index, candidate ->
                    val evidence = evidenceByKey.getValue(candidate.stableEvidenceKey)
                    check(evidence.packId == pack.packId && evidence.packVersion == pack.version)
                    check(evidence.language in filter.languages)
                    check(EvidenceEligibility.matchesMadhhab(evidence.madhhabScope, filter.selectedMadhhab))
                    check(EvidenceEligibility.isEligible(evidence.source, filter.asOfEpochMillis))
                    output += DenseRankedEvidence(channel, index + 1, evidence)
                }
            }
        }
        return output
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 2048
        const val MAX_PAGE_SIZE = 2048
        private const val ACTIVE_STATE = "ACTIVE"
    }
}
