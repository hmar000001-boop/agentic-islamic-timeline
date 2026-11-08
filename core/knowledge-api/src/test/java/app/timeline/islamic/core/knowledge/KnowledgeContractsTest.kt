package app.timeline.islamic.core.knowledge

import org.junit.Assert.*
import org.junit.Test

class KnowledgeContractsTest {
    @Test fun arabicSearchNormalizationDoesNotReplaceOriginalEvidence() {
        val normalized = CorpusTextNormalizer.normalize("إِنَّ الْقُرْآنَ ـ هُدًى")
        assertTrue(normalized.text.contains("القران"))
        assertTrue(normalized.tokens.isNotEmpty())
        assertFalse(normalized.ftsExpression.contains("NEAR"))
        assertTrue(normalized.ftsExpression.startsWith("\""))
    }

    @Test fun stableEvidenceIdentityIsEditionAndLocatorBound() {
        val key = EvidenceIdentity.stableKey("quran", "edition-1", "2:255", CorpusLanguage.ARABIC)
        assertEquals(64, key.length)
        assertEquals(key, EvidenceIdentity.stableKey("quran", "edition-1", "2:255", CorpusLanguage.ARABIC))
        assertNotEquals(key, EvidenceIdentity.stableKey("quran", "edition-2", "2:255", CorpusLanguage.ARABIC))
        assertNotEquals(key, EvidenceIdentity.stableKey("quran", "edition-1", "2:256", CorpusLanguage.ARABIC))
    }

    @Test fun sourceHashUsesExactOriginalUtf8Text() {
        assertNotEquals(EvidenceIdentity.contentSha256("الحمد"), EvidenceIdentity.contentSha256("الْحَمْدُ"))
    }

    @Test fun noDefaultMadhhabMeansNoSchoolSpecificFilter() {
        assertTrue(EvidenceEligibility.matchesMadhhab(MadhhabScope.HANAFI, null))
        assertTrue(EvidenceEligibility.matchesMadhhab(MadhhabScope.MALIKI, null))
        assertTrue(EvidenceEligibility.matchesMadhhab(MadhhabScope.SHAFII, null))
        assertTrue(EvidenceEligibility.matchesMadhhab(MadhhabScope.HANBALI, null))
        assertFalse(EvidenceEligibility.matchesMadhhab(MadhhabScope.MALIKI, MadhhabScope.HANAFI))
        assertTrue(EvidenceEligibility.matchesMadhhab(MadhhabScope.COMPARATIVE, MadhhabScope.HANAFI))
    }

    @Test fun unclearedOrUnreviewedSourceIsNeverEligible() {
        val cleared = CorpusSourceMetadata(
            sourceId = "source-1", sourceType = CorpusSourceType.QURAN, title = "Qur'an", author = null,
            publisher = "Publisher", editionId = "edition-1", edition = "Edition 1", rightsHolder = "Rights holder",
            licenseId = "license-1", licenseUrl = null, licenseState = LicenseState.CLEARED,
            licenseVerifiedAtEpochMillis = 100, licenseExpiresAtEpochMillis = 500,
            reviewState = ScholarlyReviewState.APPROVED, reviewerId = "reviewer-1", reviewedAtEpochMillis = 101,
            provenanceSha256 = "b".repeat(64)
        )
        assertTrue(EvidenceEligibility.isEligible(cleared, 200))
        assertFalse(EvidenceEligibility.isEligible(cleared, 500))
        assertFalse(EvidenceEligibility.isEligible(cleared.copy(licenseState = LicenseState.UNKNOWN), 200))
        assertFalse(EvidenceEligibility.isEligible(cleared.copy(reviewState = ScholarlyReviewState.PENDING), 200))
        assertFalse(EvidenceEligibility.isEligible(cleared.copy(reviewerId = null), 200))
    }
}
