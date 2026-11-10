package app.timeline.islamic.core.retrieval

import app.timeline.islamic.core.knowledge.CorpusLanguage
import app.timeline.islamic.core.knowledge.EvidenceEligibility
import app.timeline.islamic.core.knowledge.EvidencePassage
import app.timeline.islamic.core.knowledge.EvidenceIdentity
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import app.timeline.islamic.core.knowledge.RetrievalFilter
import app.timeline.islamic.core.knowledge.RetrievalRequest
import app.timeline.islamic.core.knowledge.CorpusTextNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

fun interface CorpusPackIndexSearcher {
    /** Return rows ordered by this pack's own BM25 rank. Scores from other pack databases are incomparable. */
    suspend fun search(
        pack: InstalledCorpusPackRecord,
        indexFile: File,
        query: String,
        filter: RetrievalFilter,
        limit: Int
    ): List<EvidencePassage>
}

data class DenseRankedEvidence(val channelId: String, val rank: Int, val evidence: EvidencePassage, val weight: Double = 1.0)

private data class DenseEvidenceKey(
    val channelId: String,
    val sourceId: String,
    val editionId: String,
    val provenanceSha256: String,
    val locator: String,
    val languageCode: String,
    val stableEvidenceKey: String,
    val contentSha256: String
)

/** Optional local dense index adapter. No network-backed implementation is permitted. */
fun interface LocalDenseRetriever {
    suspend fun search(
        installedPacks: List<Pair<InstalledCorpusPackRecord, File>>,
        normalizedQuery: String,
        filter: RetrievalFilter,
        limit: Int
    ): List<DenseRankedEvidence>
}

enum class RetrievalMode { EMPTY, LEXICAL_ONLY, DENSE_ONLY, HYBRID_FUSION }

data class RetrievalTrace(
    val mode: RetrievalMode,
    val lexicalPackChannels: List<String>,
    val denseChannels: List<String>,
    val inputCandidateCount: Int,
    val rejectedCandidateCount: Int,
    val rrfK: Int,
    val rrfScoreIsProbability: Boolean = false,
    val reranked: Boolean = false,
    val unavailablePackIds: List<String> = emptyList(),
    val failedPackIds: List<String> = emptyList(),
    val denseProviderFailed: Boolean = false
)

data class RetrievalResponse(val results: List<FusedEvidence>, val trace: RetrievalTrace)

/**
 * Local-only retrieval orchestrator. It treats each pack's lexical ranking as a separate RRF channel;
 * this avoids comparing BM25 values produced by different pack databases.
 */
class LocalCorpusRetriever(
    private val packsRoot: File,
    private val indexSearcher: CorpusPackIndexSearcher,
    private val denseRetriever: LocalDenseRetriever? = null,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun search(request: RetrievalRequest, installedPacks: List<InstalledCorpusPackRecord>): RetrievalResponse = withContext(Dispatchers.Default) {
        val normalized = CorpusTextNormalizer.normalize(request.queryText)
        if (normalized.tokens.isEmpty()) return@withContext emptyResponse()
        val filter = RetrievalFilter(request.languages, request.selectedMadhhab, clock())
        val usable = installedPacks.asSequence()
            .filter { it.status == InstalledCorpusPackRecord.STATUS_INSTALLED && isSafePackIdentity(it) }
            .distinctBy { it.stableId }
            .sortedBy { it.stableId }
            .map { it to File(packsRoot, it.relativeIndexPath) }
            .toList()
        val unavailable = usable.filterNot { it.second.isFile }.map { it.first.packId }
        val present = usable.filter { it.second.isFile }
        val failedPacks = mutableSetOf<String>()
        val now = filter.asOfEpochMillis
        var rejected = 0
        val ranked = mutableListOf<RankedEvidence>()
        val lexicalChannels = mutableListOf<String>()
        for ((pack, indexFile) in present) {
            val channelId = "lexical:${pack.packId}@${pack.version}"
            val candidates = try {
                indexSearcher.search(pack, indexFile, normalized.ftsExpression, filter, request.maxCandidatesPerPack)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                failedPacks += pack.packId
                emptyList()
            }
            candidates.forEachIndexed { index, evidence ->
                if (isEligibleHit(evidence, pack, request, now)) {
                    ranked += RankedEvidence(channelId, index + 1, evidence)
                    lexicalChannels += channelId
                } else rejected++
            }
        }
        var denseProviderFailed = false
        val denseHits = denseRetriever?.let { provider ->
            try { provider.search(present, normalized.text, filter, request.maxCandidatesPerPack) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { denseProviderFailed = true; emptyList() }
        }.orEmpty()
        val acceptedDenseChannels = mutableSetOf<String>()
        val validDenseHits = denseHits.filter { hit ->
            val installed = present.firstOrNull { it.first.packId == hit.evidence.packId && it.first.version == hit.evidence.packVersion }?.first
            val valid = installed != null && hit.channelId.isNotBlank() && hit.channelId.length <= 128 &&
                hit.rank in 1..request.maxCandidatesPerPack && hit.weight.isFinite() && hit.weight > 0.0 && hit.weight <= 10.0 &&
                isEligibleHit(hit.evidence, installed, request, now)
            if (!valid) rejected++
            valid
        }
        // An inconsistent provider must not poison the entire retrieval request. If one channel
        // assigns different weights to duplicate copies of the same evidence, reject that group.
        validDenseHits.groupBy { hit ->
            val e = hit.evidence
            DenseEvidenceKey(
                channelId = hit.channelId,
                sourceId = e.source.sourceId,
                editionId = e.source.editionId,
                provenanceSha256 = e.source.provenanceSha256,
                locator = e.locator,
                languageCode = e.language.code,
                stableEvidenceKey = e.stableEvidenceKey,
                contentSha256 = e.contentSha256
            )
        }.values.forEach { sameEvidenceFromChannel ->
            if (sameEvidenceFromChannel.map { it.weight }.distinct().size != 1) {
                rejected += sameEvidenceFromChannel.size
                return@forEach
            }
            sameEvidenceFromChannel.forEach { hit ->
                // Namespace provider-defined ids so a dense provider can never collide with
                // the deterministic lexical:pack@version channel identifiers.
                val channelId = "dense:${hit.channelId}"
                ranked += RankedEvidence(channelId, hit.rank, hit.evidence, hit.weight)
                acceptedDenseChannels += channelId
            }
        }
        val fused = RrfFusion.fuse(ranked, request.maxResults)
        val mode = when {
            fused.isEmpty() -> RetrievalMode.EMPTY
            acceptedDenseChannels.isNotEmpty() && lexicalChannels.isNotEmpty() -> RetrievalMode.HYBRID_FUSION
            acceptedDenseChannels.isNotEmpty() -> RetrievalMode.DENSE_ONLY
            else -> RetrievalMode.LEXICAL_ONLY
        }
        RetrievalResponse(
            fused,
            RetrievalTrace(
                mode = mode,
                lexicalPackChannels = lexicalChannels.distinct().sorted(),
                denseChannels = acceptedDenseChannels.sorted(),
                inputCandidateCount = ranked.size + rejected,
                rejectedCandidateCount = rejected,
                rrfK = RrfFusion.DEFAULT_K,
                unavailablePackIds = unavailable.sorted(),
                failedPackIds = failedPacks.sorted(),
                denseProviderFailed = denseProviderFailed
            )
        )
    }

    private fun isEligibleHit(
        evidence: EvidencePassage,
        pack: InstalledCorpusPackRecord,
        request: RetrievalRequest,
        now: Long
    ): Boolean {
        if (evidence.packId != pack.packId || evidence.packVersion != pack.version) return false
        if (!EvidenceEligibility.isEligible(evidence.source, now)) return false
        if (evidence.language !in request.languages) return false
        if (!EvidenceEligibility.matchesMadhhab(evidence.madhhabScope, request.selectedMadhhab)) return false
        if (evidence.locator.isBlank() || evidence.originalText.isEmpty() || evidence.stableEvidenceKey.isBlank()) return false
        if (evidence.stableEvidenceKey != EvidenceIdentity.stableKey(
                evidence.source.sourceId, evidence.source.editionId, evidence.locator, evidence.language
            )) return false
        if (CorpusTextNormalizer.normalize(evidence.originalText).text != evidence.normalizedText) return false
        return sha256(evidence.originalText) == evidence.contentSha256
    }

    private fun isSafePackIdentity(record: InstalledCorpusPackRecord): Boolean =
        record.packId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) &&
            record.version.matches(Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?"))

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun emptyResponse() = RetrievalResponse(
        emptyList(), RetrievalTrace(RetrievalMode.EMPTY, emptyList(), emptyList(), 0, 0, RrfFusion.DEFAULT_K)
    )
}
