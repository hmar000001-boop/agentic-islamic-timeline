package app.timeline.islamic.core.retrieval

import app.timeline.islamic.core.knowledge.*
import org.junit.Assert.*
import org.junit.Test

class RrfFusionTest {
    private val source = CorpusSourceMetadata(
        sourceId = "source", sourceType = CorpusSourceType.HADITH, title = "Hadith source", author = null,
        publisher = "Publisher", editionId = "ed-1", edition = "1", rightsHolder = "Rights", licenseId = "L1",
        licenseUrl = null, licenseState = LicenseState.CLEARED, licenseVerifiedAtEpochMillis = 1,
        licenseExpiresAtEpochMillis = null, reviewState = ScholarlyReviewState.APPROVED,
        reviewerId = "reviewer", reviewedAtEpochMillis = 2, provenanceSha256 = "b".repeat(64)
    )

    private fun evidence(id: String) = EvidencePassage(
        packId = "pack", packVersion = "1.0.0", passageId = id, stableEvidenceKey = "key-$id", source = source,
        locator = "book/chapter/$id", language = CorpusLanguage.ENGLISH, originalText = "Text $id",
        normalizedText = "text $id", contentSha256 = id.padStart(64, '0'), madhhabScope = MadhhabScope.ALL
    )

    @Test fun independentChannelsAreCombinedByRankNotRawScore() {
        val a = evidence("1")
        val b = evidence("2")
        val result = RrfFusion.fuse(
            listOf(
                RankedEvidence("lexical:pack-a", 1, a),
                RankedEvidence("lexical:pack-b", 1, b),
                RankedEvidence("dense:model-x", 1, b),
                RankedEvidence("dense:model-x", 2, a)
            ), maxResults = 10
        )
        assertEquals("key-2", result.first().evidence.stableEvidenceKey)
        assertEquals(2, result.first().contributions.size)
    }

    @Test fun duplicateHitsFromOneChannelCannotInflateScore() {
        val item = evidence("3")
        val hits = listOf(
            RankedEvidence("lexical:pack-a", 1, item),
            RankedEvidence("lexical:pack-a", 2, item)
        )
        val result = RrfFusion.fuse(hits, maxResults = 3)
        assertEquals(1, result.size)
        assertEquals(1, result.single().contributions.size)
        assertEquals(1.0 / 61.0, result.single().rrfScore, 0.00000001)
    }

    @Test fun identicalStableKeyFromDifferentSourceEditionsIsNotMerged() {
        val first = evidence("5").copy(stableEvidenceKey = "shared", locator = "book/1")
        val second = first.copy(
            passageId = "6",
            source = first.source.copy(editionId = "ed-2", edition = "2"),
            contentSha256 = "6".padStart(64, '0')
        )
        val result = RrfFusion.fuse(
            listOf(RankedEvidence("lexical:a", 1, first), RankedEvidence("lexical:b", 1, second)),
            maxResults = 10
        )
        assertEquals(2, result.size)
        assertEquals(1, result[0].contributions.size)
        assertEquals(1, result[1].contributions.size)
    }

    @Test fun tiedResultsHaveDeterministicOrderingIndependentOfInputOrder() {
        val first = evidence("7")
        val second = evidence("8")
        val entries = listOf(
            RankedEvidence("lexical:a", 1, first),
            RankedEvidence("lexical:b", 1, second)
        )
        val forward = RrfFusion.fuse(entries, maxResults = 10).map { it.evidence.passageId }
        val reversed = RrfFusion.fuse(entries.reversed(), maxResults = 10).map { it.evidence.passageId }
        assertEquals(forward, reversed)
    }

    @Test fun invalidRanksOrWeightsAreRejected() {
        val item = evidence("4")
        assertThrows(IllegalArgumentException::class.java) { RrfFusion.fuse(listOf(RankedEvidence("x", 0, item)), 1) }
        assertThrows(IllegalArgumentException::class.java) { RrfFusion.fuse(listOf(RankedEvidence("x", 1, item, Double.NaN)), 1) }
        assertThrows(IllegalArgumentException::class.java) { RrfFusion.fuse(listOf(RankedEvidence("x", 1, item, 11.0)), 1) }
        assertThrows(IllegalArgumentException::class.java) {
            RrfFusion.fuse(listOf(RankedEvidence("x", 1, item, 1.0), RankedEvidence("x", 2, item, 2.0)), 1)
        }
    }
}
