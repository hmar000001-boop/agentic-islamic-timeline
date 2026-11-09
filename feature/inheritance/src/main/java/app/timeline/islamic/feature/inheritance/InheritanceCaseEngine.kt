package app.timeline.islamic.feature.inheritance

import java.math.BigInteger

/** Civil and fiqh context are separate; no heir share is inferred by this intake engine. */
enum class Madhhab { HANAFI, MALIKI, SHAFII, HANBALI }
enum class EstateAmountKind { GROSS_ASSETS, SECURED_DEBT, OTHER_DEBT, FUNERAL_COST, BEQUEST }

data class EstateAmount(val kind: EstateAmountKind, val currencyCode: String, val minorUnits: Long, val userConfirmed: Boolean) {
    init { require(currencyCode.matches(Regex("[A-Z]{3}")) && minorUnits >= 0) }
}

data class FamilyFacts(
    val spouseCount: Int,
    val sons: Int,
    val daughters: Int,
    val fatherAlive: Boolean,
    val motherAlive: Boolean,
    val paternalGrandfatherAlive: Boolean,
    val maternalGrandmotherAlive: Boolean,
    val fullBrothers: Int,
    val fullSisters: Int,
    val paternalBrothers: Int,
    val paternalSisters: Int,
    val maternalSiblings: Int,
    val otherPotentialHeirsDescription: String,
    /** Explicit assertion that every potentially relevant heir category was reviewed. */
    val allPotentialHeirsEnumerated: Boolean = false
) {
    init {
        require(spouseCount in 0..4 && sons in 0..1_000 && daughters in 0..1_000)
        require(fullBrothers in 0..1_000 && fullSisters in 0..1_000 && paternalBrothers in 0..1_000 &&
            paternalSisters in 0..1_000 && maternalSiblings in 0..1_000)
        require(otherPotentialHeirsDescription.length <= 4_000)
    }
}

data class InheritanceCase(
    val caseId: String,
    val jurisdictionId: String,
    val selectedMadhhab: Madhhab?,
    val currencyCode: String,
    val amounts: List<EstateAmount>,
    val familyFacts: FamilyFacts,
    val factsConfirmed: Boolean,
    val notes: String
) {
    init {
        require(caseId.isNotBlank() && jurisdictionId.isNotBlank())
        require(currencyCode.matches(Regex("[A-Z]{3}")))
        require(amounts.size <= 100 && amounts.all { it.currencyCode == currencyCode })
        require(notes.length <= 8_000)
    }
}

data class EstateArithmeticSummary(
    val grossAssetsMinor: Long,
    val securedDebtsMinor: Long,
    val otherDebtsMinor: Long,
    val funeralCostsMinor: Long,
    val bequestInputMinor: Long,
    val provisionalRemainderMinor: Long?,
    val currencyCode: String,
    val distributionStatus: DistributionStatus,
    val reason: String
)
enum class DistributionStatus { INTAKE_ONLY, NEEDS_JURISDICTION_AND_MADHHAB, NEEDS_APPROVED_RULESET, NEEDS_CONFIRMED_FACTS }

/**
 * Phase 6 intake and arithmetic only. No fixed shares, exclusion rules, awl/radd or wasiyya limits are
 * embedded here. A future solver must load a signed, jurisdiction- and madhhab-reviewed ruleset.
 */
object InheritanceCaseEngine {
    fun summarize(input: InheritanceCase): EstateArithmeticSummary {
        if (!input.factsConfirmed || !input.familyFacts.allPotentialHeirsEnumerated || input.amounts.any { !it.userConfirmed }) {
            return EstateArithmeticSummary(0, 0, 0, 0, 0, null, input.currencyCode,
                DistributionStatus.NEEDS_CONFIRMED_FACTS, "No estate arithmetic performed because family facts, complete heir enumeration or financial amounts are unconfirmed.")
        }
        val gross = sum(input, EstateAmountKind.GROSS_ASSETS)
        val secured = sum(input, EstateAmountKind.SECURED_DEBT)
        val debts = sum(input, EstateAmountKind.OTHER_DEBT)
        val funeral = sum(input, EstateAmountKind.FUNERAL_COST)
        val bequest = sum(input, EstateAmountKind.BEQUEST)
        val remaining = (BigInteger.valueOf(gross) - BigInteger.valueOf(secured) - BigInteger.valueOf(debts)
            - BigInteger.valueOf(funeral) - BigInteger.valueOf(bequest)).max(BigInteger.ZERO).longValueExact()
        val status = when {
            !input.factsConfirmed -> DistributionStatus.NEEDS_CONFIRMED_FACTS
            input.selectedMadhhab == null || input.jurisdictionId.isBlank() -> DistributionStatus.NEEDS_JURISDICTION_AND_MADHHAB
            else -> DistributionStatus.NEEDS_APPROVED_RULESET
        }
        return EstateArithmeticSummary(gross, secured, debts, funeral, bequest, remaining, input.currencyCode,
            status, "No heir shares calculated: an approved jurisdiction- and madhhab-specific ruleset is not installed.")
    }

    private fun sum(input: InheritanceCase, kind: EstateAmountKind): Long {
        require(input.amounts.filter { it.kind == kind }.all { it.userConfirmed }) { "Unconfirmed estate amount" }
        return input.amounts.filter { it.kind == kind }.fold(0L) { total, item -> Math.addExact(total, item.minorUnits) }
    }
}
