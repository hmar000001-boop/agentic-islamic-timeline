package app.timeline.islamic.core.retrieval

import app.timeline.islamic.core.knowledge.CorpusLanguage
import app.timeline.islamic.core.knowledge.CorpusSourceMetadata
import app.timeline.islamic.core.knowledge.CorpusSourceType
import app.timeline.islamic.core.knowledge.EvidenceIdentity
import app.timeline.islamic.core.knowledge.EvidencePassage
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import app.timeline.islamic.core.knowledge.LicenseState
import app.timeline.islamic.core.knowledge.MadhhabScope
import app.timeline.islamic.core.knowledge.RetrievalRequest
import app.timeline.islamic.core.knowledge.ScholarlyReviewState
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCorpusRetrieverTest {
    private val now = 2_000L
    private val pack = InstalledCorpusPackRecord(
        packId = "fixture-pack", version = "1.0.0", manifestSha256 = "a".repeat(64),
        installedAtEpochMillis = 1_000L, licenseProfile = "fixture-cleared"
    )

    @Test fun rechecksStableEvidenceIdentityBeforeReturningSearchHit(): Unit = runBlocking {
        val root = createPackRoot()
        try {
            val tampered = evidence().copy(stableEvidenceKey = "f".repeat(64))
            val retriever = LocalCorpusRetriever(root, CorpusPackIndexSearcher { _, _, _, _, _ -> listOf(tampered) }, clock = { now })
            val result = retriever.search(request(), listOf(pack))
            assertTrue(result.results.isEmpty())
            assertEquals(1, result.trace.rejectedCandidateCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsNormalizedTextThatWasChangedAfterPackValidation(): Unit = runBlocking {
        val root = createPackRoot()
        try {
            val tampered = evidence().copy(normalizedText = "unrelated injected terms")
            val retriever = LocalCorpusRetriever(root, CorpusPackIndexSearcher { _, _, _, _, _ -> listOf(tampered) }, clock = { now })
            val result = retriever.search(request(), listOf(pack))
            assertTrue(result.results.isEmpty())
            assertEquals(1, result.trace.rejectedCandidateCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun reportsDenseProviderFailureWhileReturningEligibleLexicalResults(): Unit = runBlocking {
        val root = createPackRoot()
        try {
            val valid = evidence()
            val retriever = LocalCorpusRetriever(
                packsRoot = root,
                indexSearcher = CorpusPackIndexSearcher { _, _, _, _, _ -> listOf(valid) },
                denseRetriever = LocalDenseRetriever { _, _, _, _ -> error("simulated local vector-index failure") },
                clock = { now }
            )
            val result = retriever.search(request(), listOf(pack))
            assertEquals(1, result.results.size)
            assertEquals(RetrievalMode.LEXICAL_ONLY, result.trace.mode)
            assertTrue(result.trace.denseProviderFailed)
            assertFalse(result.trace.reranked)
            assertFalse(result.trace.rrfScoreIsProbability)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsDenseHitWithOutOfBoundsWeightWithoutBreakingLexicalFallback(): Unit = runBlocking {
        val root = createPackRoot()
        try {
            val valid = evidence()
            val retriever = LocalCorpusRetriever(
                packsRoot = root,
                indexSearcher = CorpusPackIndexSearcher { _, _, _, _, _ -> listOf(valid) },
                denseRetriever = LocalDenseRetriever { _, _, _, _ ->
                    listOf(DenseRankedEvidence("local-vector", 1, valid, weight = 11.0))
                },
                clock = { now }
            )
            val result = retriever.search(request(), listOf(pack))
            assertEquals(1, result.results.size)
            assertEquals(1, result.trace.rejectedCandidateCount)
            assertEquals(RetrievalMode.LEXICAL_ONLY, result.trace.mode)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsConflictingDuplicateDenseWeightsButKeepsLexicalFallback(): Unit = runBlocking {
        val root = createPackRoot()
        try {
            val valid = evidence()
            val retriever = LocalCorpusRetriever(
                packsRoot = root,
                indexSearcher = CorpusPackIndexSearcher { _, _, _, _, _ -> listOf(valid) },
                denseRetriever = LocalDenseRetriever { _, _, _, _ ->
                    listOf(
                        DenseRankedEvidence("local-vector", 1, valid, weight = 1.0),
                        DenseRankedEvidence("local-vector", 2, valid.copy(passageId = "duplicate-row"), weight = 2.0)
                    )
                },
                clock = { now }
            )
            val result = retriever.search(request(), listOf(pack))
            assertEquals(1, result.results.size)
            assertEquals(2, result.trace.rejectedCandidateCount)
            assertEquals(RetrievalMode.LEXICAL_ONLY, result.trace.mode)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun request() = RetrievalRequest(
        queryText = "الله", languages = setOf(CorpusLanguage.ARABIC), maxResults = 5, maxCandidatesPerPack = 20
    )

    private fun createPackRoot(): File {
        val root = Files.createTempDirectory("timeline-retrieval-").toFile()
        val index = File(root, pack.relativeIndexPath)
        check(index.parentFile?.mkdirs() == true)
        index.writeBytes(byteArrayOf(1))
        return root
    }

    private fun evidence(): EvidencePassage {
        val source = CorpusSourceMetadata(
            sourceId = "quran-source", sourceType = CorpusSourceType.QURAN, title = "Qur'an", author = null,
            publisher = "Fixture publisher", editionId = "edition-1", edition = "Edition 1",
            rightsHolder = "Fixture rights holder", licenseId = "fixture-license", licenseUrl = null,
            licenseState = LicenseState.CLEARED, licenseVerifiedAtEpochMillis = 1_000L,
            licenseExpiresAtEpochMillis = null, reviewState = ScholarlyReviewState.APPROVED,
            reviewerId = "reviewer-1", reviewedAtEpochMillis = 1_100L, provenanceSha256 = "b".repeat(64)
        )
        val text = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ"
        return EvidencePassage(
            packId = pack.packId, packVersion = pack.version, passageId = "ayah-2-255",
            stableEvidenceKey = EvidenceIdentity.stableKey(source.sourceId, source.editionId, "2:255", CorpusLanguage.ARABIC),
            source = source, locator = "2:255", language = CorpusLanguage.ARABIC,
            originalText = text, normalizedText = "الله لا اله الا هو",
            contentSha256 = EvidenceIdentity.contentSha256(text), madhhabScope = MadhhabScope.ALL
        )
    }
}
