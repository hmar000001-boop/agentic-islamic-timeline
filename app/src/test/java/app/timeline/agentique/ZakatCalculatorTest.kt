package app.timeline.agentique

import app.timeline.agentique.zakat.NisabBasis
import app.timeline.agentique.zakat.ZakatCalculator
import app.timeline.agentique.zakat.ZakatInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZakatCalculatorTest {
    @Test fun incompleteInputGivesNoResult() {
        assertNull(ZakatCalculator.calculate(ZakatInput(cash = 1000.0)))
        assertNull(ZakatCalculator.calculate(ZakatInput(cash = 1000.0, basis = NisabBasis.GOLD)))
        assertNull(ZakatCalculator.calculate(ZakatInput(goldGrams = 10.0, silverPrice = 1.0, basis = NisabBasis.SILVER)))
    }

    @Test fun silverBasisDueIsTwoAndHalfPercent() {
        val r = ZakatCalculator.calculate(ZakatInput(cash = 10_000.0, silverPrice = 3.0, basis = NisabBasis.SILVER, hawlComplete = true))!!
        assertEquals(595 * 3.0, r.nisab, 1e-9)
        assertTrue(r.reachedNisab)
        assertEquals(250.0, r.due, 1e-9)
    }

    @Test fun debtsReduceNetAndCanDropBelowNisab() {
        val r = ZakatCalculator.calculate(
            ZakatInput(cash = 10_000.0, debtsDue = 9_000.0, silverPrice = 3.0, basis = NisabBasis.SILVER, hawlComplete = true),
        )!!
        assertEquals(1_000.0, r.net, 1e-9)
        assertFalse(r.reachedNisab)
        assertEquals(0.0, r.due, 1e-9)
    }

    @Test fun noHawlMeansNothingDue() {
        val r = ZakatCalculator.calculate(ZakatInput(cash = 100_000.0, goldPrice = 60.0, basis = NisabBasis.GOLD, hawlComplete = false))!!
        assertTrue(r.reachedNisab)
        assertEquals(0.0, r.due, 1e-9)
    }

    @Test fun goldGramsAreValuedAtGoldPrice() {
        val r = ZakatCalculator.calculate(ZakatInput(goldGrams = 100.0, goldPrice = 60.0, basis = NisabBasis.GOLD, hawlComplete = true))!!
        assertNotNull(r)
        assertEquals(6_000.0, r.net, 1e-9)
        assertEquals(85 * 60.0, r.nisab, 1e-9)
        assertEquals(150.0, r.due, 1e-9)
    }
}
