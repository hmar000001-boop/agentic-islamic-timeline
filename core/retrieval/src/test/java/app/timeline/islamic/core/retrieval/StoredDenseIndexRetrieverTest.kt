package app.timeline.islamic.core.retrieval

import app.timeline.islamic.core.knowledge.CorpusLanguage
import app.timeline.islamic.core.knowledge.CorpusSourceMetadata
import app.timeline.islamic.core.knowledge.CorpusSourceType
import app.timeline.islamic.core.knowledge.DenseEmbeddingIndexStore
import app.timeline.islamic.core.knowledge.DenseIndexGeneration
import app.timeline.islamic.core.knowledge.DenseIndexVectorRecord
import app.timeline.islamic.core.knowledge.EvidenceIdentity
import app.timeline.islamic.core.knowledge.EvidenceEligibility
import app.timeline.islamic.core.knowledge.EvidencePassage
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import app.timeline.islamic.core.knowledge.LicenseState
import app.timeline.islamic.core.knowledge.LocalEmbeddingModelDescriptor
import app.timeline.islamic.core.knowledge.LocalTextEmbeddingModel
import app.timeline.islamic.core.knowledge.MadhhabScope
import app.timeline.islamic.core.knowledge.QuantizedEmbeddingVector
import app.timeline.islamic.core.knowledge.RetrievalFilter
import app.timeline.islamic.core.knowledge.RetrievalRequest
import app.timeline.islamic.core.knowledge.ScholarlyReviewState
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredDenseIndexRetrieverTest {
    private val now = 2_000L
    private val pack = InstalledCorpusPackRecord("pack", "1.0.0", "a".repeat(64), 1_000L, "cleared")
    private val descriptor = LocalEmbeddingModelDescriptor("embedding-model", "2.0", "b".repeat(64), 16)
    private val filter = RetrievalFilter(setOf(CorpusLanguage.ARABIC), null, now)

    @Test fun exhaustivePagedCosineScanReturnsStableTopKWithoutLoadingWholeIndex(): Unit = runBlocking {
        val root = Files.createTempDirectory("dense-retrieval-test-").toFile()
        val file = File(root, pack.relativeIndexPath).apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
        try {
            val evidence = listOf(evidence("2:1"), evidence("2:2"), evidence("2:3"))
            val vectors = listOf(
                DenseIndexVectorRecord(evidence[0].stableEvidenceKey, vector(1f, 0f)),
                DenseIndexVectorRecord(evidence[1].stableEvidenceKey, vector(0.8f, 0.6f)),
                DenseIndexVectorRecord(evidence[2].stableEvidenceKey, vector(-1f, 0f))
            ).sortedBy { it.stableEvidenceKey }
            val store = FixtureStore(pack, descriptor, vectors)
            val model = FixtureModel(descriptor)
            val lookup = lookupFor(evidence)
            val retriever = StoredDenseIndexRetriever(store, model, lookup, pageSize = 2)

            val hits = retriever.search(listOf(pack to file), "query", filter, limit = 2)

            assertEquals(2, hits.size)
            assertEquals(evidence[0].stableEvidenceKey, hits[0].evidence.stableEvidenceKey)
            assertEquals(evidence[1].stableEvidenceKey, hits[1].evidence.stableEvidenceKey)
            assertEquals(listOf(1, 2), hits.map { it.rank })
            assertEquals(3L, store.scannedRows)
            assertEquals(3, store.pageCalls) // two data pages plus one terminal empty page
            assertEquals(1, model.queryCalls)
            assertTrue(hits.all { it.channelId.startsWith("embedding-model:") && it.channelId.length <= 128 })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun staleCorpusManifestOrEmbeddingProfileCannotActivateADenseGeneration(): Unit = runBlocking {
        val root = Files.createTempDirectory("dense-fingerprint-test-").toFile()
        val file = File(root, pack.relativeIndexPath).apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
        try {
            val item = evidence("2:4")
            val store = FixtureStore(
                pack.copy(manifestSha256 = "c".repeat(64)), descriptor,
                listOf(DenseIndexVectorRecord(item.stableEvidenceKey, vector(1f, 0f)))
            )
            val result = StoredDenseIndexRetriever(
                store, FixtureModel(descriptor),
                lookupFor(listOf(item))
            ).search(listOf(pack to file), "query", filter, 5)
            assertTrue(result.isEmpty())
            assertEquals(0, store.pageCalls)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun fullPassageTextIsHydratedOnlyForTheFinalTopKAfterTheCompleteScan(): Unit = runBlocking {
        val root = Files.createTempDirectory("dense-top-k-hydration-test-").toFile()
        val file = File(root, pack.relativeIndexPath).apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
        try {
            val passages = listOf(evidence("2:9"), evidence("2:10"), evidence("2:11"))
            val vectors = passages.mapIndexed { index, passage ->
                DenseIndexVectorRecord(passage.stableEvidenceKey, vector(if (index == 0) 1f else 0.25f, 0f))
            }.sortedBy { it.stableEvidenceKey }
            val store = FixtureStore(pack, descriptor, vectors)
            var scannedKeys = 0
            var hydratedKeys = emptyList<String>()
            val lookup = object : DenseEvidenceLookup {
                override suspend fun readEligibleEvidenceKeysByStableKeys(
                    pack: InstalledCorpusPackRecord, indexFile: File, stableEvidenceKeys: List<String>, filter: RetrievalFilter
                ): Set<String> {
                    scannedKeys += stableEvidenceKeys.size
                    return passages.filter { it.stableEvidenceKey in stableEvidenceKeys }.map { it.stableEvidenceKey }.toSet()
                }
                override suspend fun readEvidenceByStableKeys(
                    pack: InstalledCorpusPackRecord, indexFile: File, stableEvidenceKeys: List<String>, filter: RetrievalFilter
                ): List<EvidencePassage> {
                    hydratedKeys = stableEvidenceKeys
                    return passages.filter { it.stableEvidenceKey in stableEvidenceKeys }
                }
            }
            val results = StoredDenseIndexRetriever(store, FixtureModel(descriptor), lookup, pageSize = 2)
                .search(listOf(pack to file), "query", filter, limit = 1)
            assertEquals(3, scannedKeys)
            assertEquals(1, hydratedKeys.size)
            assertEquals(1, results.size)
            assertEquals(hydratedKeys.single(), results.single().evidence.stableEvidenceKey)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun storedDenseRetrieverIsFusedWithFtsLexicalEvidenceByTheApplicationOrchestrator(): Unit = runBlocking {
        val root = Files.createTempDirectory("dense-hybrid-fusion-test-").toFile()
        File(root, pack.relativeIndexPath).apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
        try {
            val item = evidence("2:8")
            val store = FixtureStore(
                pack, descriptor,
                listOf(DenseIndexVectorRecord(item.stableEvidenceKey, vector(1f, 0f)))
            )
            val dense = StoredDenseIndexRetriever(
                store, FixtureModel(descriptor),
                lookupFor(listOf(item)),
                pageSize = 1
            )
            val orchestrator = LocalCorpusRetriever(
                packsRoot = root,
                indexSearcher = CorpusPackIndexSearcher { _, _, _, _, _ -> listOf(item) },
                denseRetriever = dense,
                clock = { now }
            )
            val response = orchestrator.search(
                RetrievalRequest("Allah", setOf(CorpusLanguage.ARABIC), maxResults = 5, maxCandidatesPerPack = 10),
                listOf(pack)
            )
            assertEquals(RetrievalMode.HYBRID_FUSION, response.trace.mode)
            assertEquals(1, response.results.size)
            val channels = response.results.single().contributions.map { it.channelId }.toSet()
            assertTrue("lexical:pack@1.0.0" in channels)
            assertTrue(channels.single { it.startsWith("dense:") }.startsWith("dense:embedding-model:"))
            assertFalse(response.trace.rrfScoreIsProbability)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun queryNormalizationIsStableAndCosineMatchesTheConvenienceScorer() {
        val stored = vector(0.8f, 0.6f)
        val rawQuery = FloatArray(descriptor.dimension).also { it[0] = 3f; it[1] = 4f }
        val normalized = QuantizedEmbeddingCodec.normalizeQuery(rawQuery)
        assertTrue(kotlin.math.abs(normalized.sumOf { it.toDouble() * it.toDouble() } - 1.0) < 1e-6)
        assertTrue(kotlin.math.abs(
            QuantizedEmbeddingCodec.cosine(stored, rawQuery) -
                QuantizedEmbeddingCodec.cosineWithNormalizedQuery(stored, normalized)
        ) < 1e-9)
    }

    @Test fun changedTaskPrefixProducesDifferentEmbeddingProfileFingerprint() {
        val otherProfile = LocalEmbeddingModelDescriptor(
            modelId = descriptor.modelId,
            modelVersion = descriptor.modelVersion,
            artifactSha256 = descriptor.artifactSha256,
            dimension = descriptor.dimension,
            maximumInputCharacters = descriptor.maximumInputCharacters,
            queryPrefix = "task: a different query prefix | text: "
        )
        assertFalse(descriptor.embeddingProfileSha256 == otherProfile.embeddingProfileSha256)
    }

    @Test fun detectsMissingRowsInsteadOfReturningAFalseCompleteRanking(): Unit = runBlocking {
        val root = Files.createTempDirectory("dense-count-test-").toFile()
        val file = File(root, pack.relativeIndexPath).apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
        try {
            val item = evidence("2:5")
            val store = FixtureStore(pack, descriptor, listOf(DenseIndexVectorRecord(item.stableEvidenceKey, vector(1f, 0f))))
            store.reportedCount = 2
            val failure = runCatching {
                StoredDenseIndexRetriever(store, FixtureModel(descriptor), lookupFor(listOf(item)))
                    .search(listOf(pack to file), "query", filter, 5)
            }
            assertTrue(failure.isFailure)
            assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("vector count"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun queryFilterIsAppliedBeforeDenseCandidatesAreRanked(): Unit = runBlocking {
        val root = Files.createTempDirectory("dense-filter-test-").toFile()
        val file = File(root, pack.relativeIndexPath).apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
        try {
            val arabic = evidence("2:6")
            val english = evidence("2:7").copy(language = CorpusLanguage.ENGLISH,
                stableEvidenceKey = EvidenceIdentity.stableKey("quran", "ed-1", "2:7", CorpusLanguage.ENGLISH))
            val rows = listOf(arabic, english).map { DenseIndexVectorRecord(it.stableEvidenceKey, vector(1f, 0f)) }
                .sortedBy { it.stableEvidenceKey }
            val store = FixtureStore(pack, descriptor, rows)
            val results = StoredDenseIndexRetriever(
                store, FixtureModel(descriptor),
                object : DenseEvidenceLookup {
                    override suspend fun readEligibleEvidenceKeysByStableKeys(
                        pack: InstalledCorpusPackRecord, indexFile: File, stableEvidenceKeys: List<String>, filter: RetrievalFilter
                    ) = listOf(arabic, english).filter { it.stableEvidenceKey in stableEvidenceKeys && it.language in filter.languages }
                        .map { it.stableEvidenceKey }.toSet()
                    override suspend fun readEvidenceByStableKeys(
                        pack: InstalledCorpusPackRecord, indexFile: File, stableEvidenceKeys: List<String>, filter: RetrievalFilter
                    ) = listOf(arabic, english).filter { it.stableEvidenceKey in stableEvidenceKeys && it.language in filter.languages }
                }, pageSize = 2
            ).search(listOf(pack to file), "query", filter, 5)
            assertEquals(listOf(arabic.stableEvidenceKey), results.map { it.evidence.stableEvidenceKey })
        } finally {
            root.deleteRecursively()
        }
    }

    private fun lookupFor(evidence: List<EvidencePassage>): DenseEvidenceLookup = object : DenseEvidenceLookup {
        override suspend fun readEligibleEvidenceKeysByStableKeys(
            pack: InstalledCorpusPackRecord, indexFile: File, stableEvidenceKeys: List<String>, filter: RetrievalFilter
        ): Set<String> = evidence.filter { it.stableEvidenceKey in stableEvidenceKeys && it.language in filter.languages &&
            EvidenceEligibility.matchesMadhhab(it.madhhabScope, filter.selectedMadhhab) &&
            EvidenceEligibility.isEligible(it.source, filter.asOfEpochMillis)
        }.map { it.stableEvidenceKey }.toSet()

        override suspend fun readEvidenceByStableKeys(
            pack: InstalledCorpusPackRecord, indexFile: File, stableEvidenceKeys: List<String>, filter: RetrievalFilter
        ): List<EvidencePassage> = evidence.filter { it.stableEvidenceKey in stableEvidenceKeys }
    }

    private fun vector(first: Float, second: Float): QuantizedEmbeddingVector =
        QuantizedEmbeddingCodec.encode(FloatArray(16).also { it[0] = first; it[1] = second })

    private fun evidence(locator: String): EvidencePassage {
        val source = CorpusSourceMetadata(
            sourceId = "quran", sourceType = CorpusSourceType.QURAN, title = "Qur'an", author = null,
            publisher = "Publisher", editionId = "ed-1", edition = "Edition 1", rightsHolder = "Rights",
            licenseId = "license", licenseUrl = null, licenseState = LicenseState.CLEARED,
            licenseVerifiedAtEpochMillis = 500, licenseExpiresAtEpochMillis = null,
            reviewState = ScholarlyReviewState.APPROVED, reviewerId = "reviewer", reviewedAtEpochMillis = 600,
            provenanceSha256 = "d".repeat(64)
        )
        val text = "Original source passage $locator"
        return EvidencePassage(
            packId = pack.packId, packVersion = pack.version, passageId = "passage-$locator",
            stableEvidenceKey = EvidenceIdentity.stableKey(source.sourceId, source.editionId, locator, CorpusLanguage.ARABIC),
            source = source, locator = locator, language = CorpusLanguage.ARABIC,
            originalText = text, normalizedText = text.lowercase(),
            contentSha256 = EvidenceIdentity.contentSha256(text), madhhabScope = MadhhabScope.ALL
        )
    }

    private class FixtureModel(override val descriptor: LocalEmbeddingModelDescriptor) : LocalTextEmbeddingModel {
        var queryCalls = 0
        override suspend fun embedBatch(texts: List<String>) = texts.map { FloatArray(descriptor.dimension).also { it[0] = 1f } }
        override suspend fun embedQuery(text: String): FloatArray {
            queryCalls++
            return FloatArray(descriptor.dimension).also { it[0] = 1f }
        }
    }

    private class FixtureStore(
        private val pack: InstalledCorpusPackRecord,
        private val model: LocalEmbeddingModelDescriptor,
        private val rows: List<DenseIndexVectorRecord>
    ) : DenseEmbeddingIndexStore {
        var pageCalls = 0
        var scannedRows = 0L
        var reportedCount = rows.size.toLong()
        override suspend fun beginGeneration(pack: InstalledCorpusPackRecord, model: LocalEmbeddingModelDescriptor, maximumTotalEncodedBytes: Long) = "unused"
        override suspend fun appendVectors(generationId: String, vectors: Map<String, QuantizedEmbeddingVector>) = 0L
        override suspend fun activateGeneration(generationId: String) = Unit
        override suspend fun abortGeneration(generationId: String) = Unit
        override suspend fun removePackVersion(packId: String, packVersion: String) = 0
        override suspend fun recoverAbandonedGenerations() = 0
        override suspend fun activeGeneration(packId: String, packVersion: String, modelId: String, modelVersion: String) =
            DenseIndexGeneration(
                "generation", pack.packId, pack.version, model.modelId, model.modelVersion, model.artifactSha256,
                model.dimension, reportedCount, reportedCount * model.dimension, "ACTIVE",
                pack.manifestSha256, model.embeddingProfileSha256
            )
        override suspend fun vectorsFor(generationId: String, stableEvidenceKeys: List<String>) = emptyMap<String, QuantizedEmbeddingVector>()
        override suspend fun vectorsPage(generationId: String, afterStableEvidenceKey: String, limit: Int): List<DenseIndexVectorRecord> {
            pageCalls++
            val page = rows.filter { it.stableEvidenceKey > afterStableEvidenceKey }.take(limit)
            scannedRows += page.size
            return page
        }
    }
}
