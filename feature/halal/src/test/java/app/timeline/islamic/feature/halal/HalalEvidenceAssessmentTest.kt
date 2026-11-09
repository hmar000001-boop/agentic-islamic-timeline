package app.timeline.islamic.feature.halal

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class HalalEvidenceAssessmentTest {
    @Test fun ambiguousIngredientsNeverBecomeVerdict() {
        val input = HalalEvidenceAssessment("Sample", listOf(IngredientFinding("E-code", IngredientEvidenceState.AMBIGUOUS_ADDITIVE, null)), null, Instant.EPOCH)
        val result = HalalEvidenceEngine.assess(input)
        assertEquals("NO_HALAL_OR_HARAM_VERDICT", result.conclusion)
        assertEquals(CertificationEvidenceState.NOT_PROVIDED, result.certificationState)
    }

    @Test fun userEnteredReviewerFieldsDoNotSelfVerifyCertification() {
        val cert = CertificationEvidence("Org", "CA", "Product", "123", Instant.EPOCH,
            Instant.EPOCH.plusSeconds(100), "local:cert", Instant.EPOCH, "self-asserted", "APPROVED", true)
        val input = HalalEvidenceAssessment("Sample", emptyList(), cert, Instant.EPOCH.plusSeconds(5))
        assertEquals(CertificationEvidenceState.IDENTIFIED_REPORTED, HalalEvidenceEngine.assess(input).certificationState)
    }

    @Test fun expiredCertificationCannotBeCurrent() {
        val cert = CertificationEvidence("Org", "CA", "Product", "123", Instant.EPOCH, Instant.EPOCH.plusSeconds(10), "local:cert", Instant.EPOCH, "reviewer", "APPROVED")
        val input = HalalEvidenceAssessment("Sample", emptyList(), cert, Instant.EPOCH)
        assertEquals(CertificationEvidenceState.EXPIRED, HalalEvidenceEngine.assess(input, Instant.EPOCH.plusSeconds(11)).certificationState)
    }
}
