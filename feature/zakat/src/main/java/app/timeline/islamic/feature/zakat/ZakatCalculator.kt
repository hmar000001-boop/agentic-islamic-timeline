package app.timeline.islamic.feature.zakat

import app.timeline.islamic.core.common.toLongExactCompat

import java.math.BigInteger
import java.time.LocalDate

enum class ZakatAssetCategory { CASH, GOLD, SILVER, TRADE_GOODS, RECEIVABLES, INVESTMENTS, AGRICULTURE, LIVESTOCK, OTHER }
enum class ZakatLiabilityCategory { SHORT_TERM_DUE, LONG_TERM_DUE, OTHER }
enum class NisabReference { GOLD, SILVER, CUSTOM }

data class MoneyAmount(val currencyCode: String, val minorUnits: Long) {
    init { require(currencyCode.matches(Regex("[A-Z]{3}")) && minorUnits >= 0) }
}

data class ZakatRuleProfile(
    val id: String,
    val version: String,
    val currencyCode: String,
    val rateBasisPoints: Int,
    val includedAssets: Set<ZakatAssetCategory>,
    val deductibleLiabilities: Set<ZakatLiabilityCategory>,
    val nisabReference: NisabReference,
    val nisabAmountMinor: Long,
    val haulDays: Int
) {
    init {
        require(id.isNotBlank() && version.isNotBlank())
        require(currencyCode.matches(Regex("[A-Z]{3}")))
        require(rateBasisPoints in 1..10_000)
        require(nisabAmountMinor > 0)
        require(haulDays in 1..500)
    }
}

data class ZakatAssetInput(val category: ZakatAssetCategory, val amount: MoneyAmount, val valuationDate: LocalDate, val userConfirmed: Boolean)
data class ZakatLiabilityInput(val category: ZakatLiabilityCategory, val amount: MoneyAmount, val userConfirmed: Boolean)

data class ZakatCaseInput(
    val caseId: String,
    val ruleProfile: ZakatRuleProfile,
    val assets: List<ZakatAssetInput>,
    val liabilities: List<ZakatLiabilityInput>,
    val nisabValueConfirmed: Boolean,
    val haulStartDate: LocalDate,
    val valuationDate: LocalDate
) {
    init {
        require(caseId.isNotBlank() && assets.size <= 500 && liabilities.size <= 500)
        require(!valuationDate.isBefore(haulStartDate))
    }
}

enum class ZakatCalculationStatus { PREVIEW_ONLY_RULES_UNVERIFIED, INPUT_REVIEW_REQUIRED }
data class ZakatCalculationResult(
    val status: ZakatCalculationStatus,
    val eligibleAssetsMinor: Long,
    val deductibleLiabilitiesMinor: Long,
    val netEligibleMinor: Long,
    val nisabMinor: Long,
    val amountMinor: Long?,
    val currencyCode: String,
    val ruleProfileId: String,
    val ruleProfileVersion: String,
    val formula: String,
    val calculationNote: String
)

/** Pure arithmetic over user-confirmed figures and an explicit rule profile. Never supplies market prices. */
object ZakatCalculator {
    fun calculate(input: ZakatCaseInput): ZakatCalculationResult {
        val profile = input.ruleProfile
        if (!input.nisabValueConfirmed || input.assets.any { !it.userConfirmed } || input.liabilities.any { !it.userConfirmed } ||
            input.assets.any { it.valuationDate != input.valuationDate }) {
            return result(input, ZakatCalculationStatus.INPUT_REVIEW_REQUIRED, 0, 0, 0, null,
                "Confirm all amounts and ensure every valuation uses the explicit case date. No ruling is produced.")
        }
        require(input.assets.all { it.amount.currencyCode == profile.currencyCode }) { "Mixed asset currencies are not supported" }
        require(input.liabilities.all { it.amount.currencyCode == profile.currencyCode }) { "Mixed liability currencies are not supported" }
        if (java.time.temporal.ChronoUnit.DAYS.between(input.haulStartDate, input.valuationDate) < profile.haulDays) {
            return result(input, ZakatCalculationStatus.PREVIEW_ONLY_RULES_UNVERIFIED, 0, 0, 0, null,
                "The user-entered haul-day threshold was not reached. This is an unreviewed arithmetic preview, not a ruling.")
        }
        val assets = input.assets.filter { it.category in profile.includedAssets }.fold(0L) { a, v -> Math.addExact(a, v.amount.minorUnits) }
        val liabilities = input.liabilities.filter { it.category in profile.deductibleLiabilities }.fold(0L) { a, v -> Math.addExact(a, v.amount.minorUnits) }
        val net = (assets - liabilities).coerceAtLeast(0L)
        if (net < profile.nisabAmountMinor) return result(input, ZakatCalculationStatus.PREVIEW_ONLY_RULES_UNVERIFIED, assets, liabilities, net, 0L,
            "Net value is below the user-entered threshold. Zero is only a mathematical comparison, not a religious conclusion.")
        val amount = BigInteger.valueOf(net).multiply(BigInteger.valueOf(profile.rateBasisPoints.toLong()))
            .add(BigInteger.valueOf(9_999)).divide(BigInteger.valueOf(10_000))
            .toLongExactCompat()
        // Review flags and reviewer names are user data, not a cryptographic approval. Until a
        // signed scholar-approved ruleset registry is integrated, all results remain previews.
        return result(input, ZakatCalculationStatus.PREVIEW_ONLY_RULES_UNVERIFIED, assets, liabilities, net, amount,
            "Arithmetic completed with user-entered assumptions. The profile and its religious applicability are not independently reviewed.")
    }

    private fun result(input: ZakatCaseInput, status: ZakatCalculationStatus, assets: Long, liabilities: Long, net: Long,
                       amount: Long?, note: String) =
        ZakatCalculationResult(status, assets, liabilities, net, input.ruleProfile.nisabAmountMinor, amount,
            input.ruleProfile.currencyCode, input.ruleProfile.id, input.ruleProfile.version,
            "max(0, user-selected included assets - user-selected deductible liabilities) × user-entered rate / 10,000; rounded up to the currency minor unit",
            note)
}
