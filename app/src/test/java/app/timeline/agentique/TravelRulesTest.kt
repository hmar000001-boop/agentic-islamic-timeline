package app.timeline.agentique

import app.timeline.agentique.core.Madhhab
import app.timeline.agentique.prayer.JamStatus
import app.timeline.agentique.prayer.QasrStatus
import app.timeline.agentique.prayer.TravelRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelRulesTest {
    @Test fun shafiiTravellerMayShortenAndCombine() {
        val r = TravelRules.evaluate(Madhhab.SHAFII, 100.0, 81.0, 3)
        assertEquals(QasrStatus.PERMISSIBLE, r.qasr)
        assertEquals(JamStatus.PERMITTED_WITH_CONDITIONS, r.jam)
    }

    @Test fun hanafiShortensButDoesNotCombine() {
        val r = TravelRules.evaluate(Madhhab.HANAFI, 100.0, 81.0, 3)
        assertEquals(QasrStatus.OBLIGATORY, r.qasr)
        assertEquals(JamStatus.NOT_PERMITTED, r.jam)
    }

    @Test fun shortDistanceIsNotTravel() {
        val r = TravelRules.evaluate(Madhhab.MALIKI, 50.0, 81.0, 1)
        assertEquals(QasrStatus.NOT_TRAVELLER, r.qasr)
        assertEquals(JamStatus.NOT_TRAVELLER, r.jam)
    }

    @Test fun longStayEndsTravelStatus() {
        assertTrue(TravelRules.evaluate(Madhhab.HANAFI, 200.0, 81.0, 15).stayExceeded)
        assertEquals(QasrStatus.OBLIGATORY, TravelRules.evaluate(Madhhab.HANAFI, 200.0, 81.0, 14).qasr)
        assertEquals(QasrStatus.NOT_TRAVELLER, TravelRules.evaluate(Madhhab.SHAFII, 200.0, 81.0, 5).qasr)
        assertEquals(QasrStatus.PERMISSIBLE, TravelRules.evaluate(Madhhab.HANBALI, 200.0, 81.0, 4).qasr)
    }
}
