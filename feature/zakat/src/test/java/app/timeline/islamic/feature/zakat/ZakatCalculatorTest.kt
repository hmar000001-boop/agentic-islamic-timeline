package app.timeline.islamic.feature.zakat

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ZakatCalculatorTest {
    private fun profile() = ZakatRuleProfile("user-profile", "1", "CAD", 250,
        setOf(ZakatAssetCategory.CASH), setOf(ZakatLiabilityCategory.SHORT_TERM_DUE), NisabReference.CUSTOM,
        100_000, 354)

    @Test fun unreviewedProfileReturnsPreviewNotReligiousConclusion() {
        val input = ZakatCaseInput("case", profile(),
            listOf(ZakatAssetInput(ZakatAssetCategory.CASH, MoneyAmount("CAD", 1_000_000), LocalDate.of(2026,1,1), true)),
            emptyList(), true, LocalDate.of(2025,1,1), LocalDate.of(2026,1,1))
        val result = ZakatCalculator.calculate(input)
        assertEquals(ZakatCalculationStatus.PREVIEW_ONLY_RULES_UNVERIFIED, result.status)
        assertEquals(25_000L, result.amountMinor)
    }

    @Test fun noReviewedRulesetCanBeSelectedFromUserSuppliedFields() {
        val input = ZakatCaseInput("case", profile(),
            listOf(ZakatAssetInput(ZakatAssetCategory.CASH, MoneyAmount("CAD", 1_000_000), LocalDate.of(2026,1,1), true)),
            emptyList(), true, LocalDate.of(2025,1,1), LocalDate.of(2026,1,1))
        assertEquals(ZakatCalculationStatus.PREVIEW_ONLY_RULES_UNVERIFIED, ZakatCalculator.calculate(input).status)
    }

    @Test fun valuationsMustMatchTheExplicitCaseDate() {
        val input = ZakatCaseInput("case", profile(),
            listOf(ZakatAssetInput(ZakatAssetCategory.CASH, MoneyAmount("CAD", 1_000_000), LocalDate.of(2025,12,31), true)),
            emptyList(), true, LocalDate.of(2025,1,1), LocalDate.of(2026,1,1))
        assertEquals(ZakatCalculationStatus.INPUT_REVIEW_REQUIRED, ZakatCalculator.calculate(input).status)
    }

    @Test fun belowUserEnteredThresholdRemainsAnUnverifiedPreview() {
        val input = ZakatCaseInput("case", profile(),
            listOf(ZakatAssetInput(ZakatAssetCategory.CASH, MoneyAmount("CAD", 99_999), LocalDate.of(2026,1,1), true)),
            emptyList(), true, LocalDate.of(2025,1,1), LocalDate.of(2026,1,1))
        val result = ZakatCalculator.calculate(input)
        assertEquals(ZakatCalculationStatus.PREVIEW_ONLY_RULES_UNVERIFIED, result.status)
        assertEquals(0L, result.amountMinor)
        assertTrue(result.calculationNote.contains("not a religious conclusion"))
    }

    @Test fun unreviewedHaulThresholdCannotProduceACompletedRulingStatus() {
        val input = ZakatCaseInput("case", profile(),
            listOf(ZakatAssetInput(ZakatAssetCategory.CASH, MoneyAmount("CAD", 1_000_000), LocalDate.of(2026,1,1), true)),
            emptyList(), true, LocalDate.of(2025,6,1), LocalDate.of(2026,1,1))
        val result = ZakatCalculator.calculate(input)
        assertEquals(ZakatCalculationStatus.PREVIEW_ONLY_RULES_UNVERIFIED, result.status)
        assertEquals(null, result.amountMinor)
        assertTrue(result.calculationNote.contains("not a ruling"))
    }
    @Test fun supportsMaximumLongAmountWithoutTruncation() {
        val maximumRateProfile = profile().copy(rateBasisPoints = 10_000, nisabAmountMinor = 1)
        val input = ZakatCaseInput("case-max", maximumRateProfile,
            listOf(ZakatAssetInput(ZakatAssetCategory.CASH, MoneyAmount("CAD", Long.MAX_VALUE), LocalDate.of(2026,1,1), true)),
            emptyList(), true, LocalDate.of(2025,1,1), LocalDate.of(2026,1,1))
        val result = ZakatCalculator.calculate(input)
        assertEquals(Long.MAX_VALUE, result.amountMinor)
        assertEquals(ZakatCalculationStatus.PREVIEW_ONLY_RULES_UNVERIFIED, result.status)
    }

}
