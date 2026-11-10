package app.timeline.islamic.feature.inheritance

import org.junit.Assert.*
import org.junit.Test

class InheritanceCaseEngineTest {
    @Test fun neverDistributesWithoutReviewedRuleset() {
        val facts = FamilyFacts(1, 1, 1, true, true, false, false, 0, 0, 0, 0, 0, "", true)
        val input = InheritanceCase("case", "CA-QC", Madhhab.HANAFI, "CAD",
            listOf(EstateAmount(EstateAmountKind.GROSS_ASSETS, "CAD", 10_000_00, true), EstateAmount(EstateAmountKind.OTHER_DEBT, "CAD", 100_00, true)), facts, true, "")
        val result = InheritanceCaseEngine.summarize(input)
        assertEquals(9_900_00L, result.provisionalRemainderMinor)
        assertEquals(DistributionStatus.NEEDS_APPROVED_RULESET, result.distributionStatus)
    }

    @Test fun doesNotCalculateEstateRemainderWhenHeirEnumerationIsIncomplete() {
        val facts = FamilyFacts(1, 1, 1, true, true, false, false, 0, 0, 0, 0, 0, "", false)
        val input = InheritanceCase("case-2", "CA-QC", Madhhab.HANAFI, "CAD",
            listOf(EstateAmount(EstateAmountKind.GROSS_ASSETS, "CAD", 100_000, true)), facts, true, "")
        val result = InheritanceCaseEngine.summarize(input)
        assertNull(result.provisionalRemainderMinor)
        assertEquals(DistributionStatus.NEEDS_CONFIRMED_FACTS, result.distributionStatus)
    }

    @Test fun incompleteFinancialConfirmationDoesNotProduceArithmetic() {
        val facts = FamilyFacts(1, 1, 1, true, true, false, false, 0, 0, 0, 0, 0, "", true)
        val input = InheritanceCase("case-3", "CA-QC", Madhhab.HANAFI, "CAD",
            listOf(EstateAmount(EstateAmountKind.GROSS_ASSETS, "CAD", 100_000, false)), facts, true, "")
        val result = InheritanceCaseEngine.summarize(input)
        assertNull(result.provisionalRemainderMinor)
        assertEquals(DistributionStatus.NEEDS_CONFIRMED_FACTS, result.distributionStatus)
    }
    @Test fun doesNotOverflowAtMaximumSupportedGrossEstate() {
        val facts = FamilyFacts(1, 1, 1, true, true, false, false, 0, 0, 0, 0, 0, "", true)
        val input = InheritanceCase("case-max", "CA-QC", Madhhab.HANAFI, "CAD",
            listOf(EstateAmount(EstateAmountKind.GROSS_ASSETS, "CAD", Long.MAX_VALUE, true)), facts, true, "")
        val result = InheritanceCaseEngine.summarize(input)
        assertEquals(Long.MAX_VALUE, result.provisionalRemainderMinor)
        assertEquals(DistributionStatus.NEEDS_APPROVED_RULESET, result.distributionStatus)
    }

}
