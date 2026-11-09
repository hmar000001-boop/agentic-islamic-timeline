package app.timeline.islamic.feature.halal

import java.time.Instant

/** These are evidence states, not halal/haram verdicts. */
enum class IngredientEvidenceState {
    RECOGNIZED_INGREDIENT,
    ORIGIN_UNKNOWN,
    AMBIGUOUS_ADDITIVE,
    INSUFFICIENT_DATA
}

enum class CertificationEvidenceState {
    IDENTIFIED_REPORTED,
    EXPIRED,
    UNVERIFIED,
    PRODUCT_OR_SITE_MISMATCH,
    NOT_PROVIDED
}

data class CertificationEvidence(
    val organization: String,
    val territory: String,
    val productOrSite: String,
    val certificateNumber: String,
    val validFrom: Instant?,
    val validUntil: Instant?,
    val sourceReference: String,
    val capturedAt: Instant,
    val sourceReviewedBy: String? = null,
    val reviewState: String = "PENDING",
    val productOrSiteMatchConfirmed: Boolean = false
) {
    init {
        require(organization.isNotBlank() && territory.isNotBlank() && productOrSite.isNotBlank())
        require(certificateNumber.isNotBlank() && sourceReference.isNotBlank())
        require(validUntil == null || validFrom == null || validUntil.isAfter(validFrom))
    }
}

data class IngredientFinding(
    val labelText: String,
    val state: IngredientEvidenceState,
    val sourceReference: String?
) {
    init { require(labelText.isNotBlank() && labelText.length <= 256) }
}

data class HalalEvidenceAssessment(
    val productName: String,
    val ingredients: List<IngredientFinding>,
    val certification: CertificationEvidence?,
    val assessedAt: Instant
) {
    init {
        require(productName.isNotBlank() && productName.length <= 256)
        require(ingredients.size <= 500)
    }
}

data class HalalAssessmentSummary(
    val ingredientStates: Set<IngredientEvidenceState>,
    val certificationState: CertificationEvidenceState,
    val conclusion: String = "NO_HALAL_OR_HARAM_VERDICT",
    val requiresHumanReview: Boolean = true
)

object HalalEvidenceEngine {
    fun assess(input: HalalEvidenceAssessment, asOf: Instant = input.assessedAt): HalalAssessmentSummary {
        val cert = input.certification
        val certState = when {
            cert == null -> CertificationEvidenceState.NOT_PROVIDED
            cert.validUntil != null && !asOf.isBefore(cert.validUntil) -> CertificationEvidenceState.EXPIRED
            !cert.productOrSiteMatchConfirmed -> CertificationEvidenceState.PRODUCT_OR_SITE_MISMATCH
            cert.reviewState != "APPROVED" || cert.sourceReviewedBy.isNullOrBlank() -> CertificationEvidenceState.UNVERIFIED
            cert.sourceReference.isBlank() || cert.certificateNumber.isBlank() || cert.validFrom == null || cert.validUntil == null -> CertificationEvidenceState.UNVERIFIED
            asOf.isBefore(cert.validFrom) -> CertificationEvidenceState.UNVERIFIED
            // User-supplied metadata is not a signed authority proof; the evidence remains reported only.
            else -> CertificationEvidenceState.IDENTIFIED_REPORTED
        }
        return HalalAssessmentSummary(
            ingredientStates = input.ingredients.map { it.state }.toSet(),
            certificationState = certState,
            requiresHumanReview = true
        )
    }

    fun fromImageOcr(ocrText: String): List<IngredientFinding> {
        require(ocrText.length <= 32_768)
        // OCR is only a transcription candidate. Nothing in this parser assigns halal/haram status.
        return ocrText.lineSequence().map(String::trim).filter(String::isNotEmpty).take(500).map {
            IngredientFinding(it.take(256), IngredientEvidenceState.INSUFFICIENT_DATA, null)
        }.toList()
    }
}
