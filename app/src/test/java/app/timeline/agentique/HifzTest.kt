package app.timeline.agentique

import app.timeline.agentique.quran.Grade
import app.timeline.agentique.quran.HifzItem
import app.timeline.agentique.quran.HifzPlanner
import app.timeline.agentique.quran.Sm2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HifzTest {
    private fun item(due: Long = 0, from: Int = 1, to: Int = 5) = HifzItem("a", 2, from, to, dueDay = due)

    @Test fun goodGradesGrowTheInterval() {
        val a = Sm2.next(item(), Grade.GOOD, 100)
        assertEquals(1, a.intervalDays); assertEquals(101, a.dueDay)
        val b = Sm2.next(a, Grade.GOOD, 101)
        assertEquals(3, b.intervalDays)
        val c = Sm2.next(b, Grade.GOOD, 104)
        assertEquals(7, c.intervalDays)
    }

    @Test fun againResetsAndCountsLapse() {
        val grown = Sm2.next(Sm2.next(item(), Grade.GOOD, 1), Grade.GOOD, 2)
        val r = Sm2.next(grown, Grade.AGAIN, 5)
        assertEquals(0, r.reps); assertEquals(1, r.lapses); assertEquals(6, r.dueDay)
        assertTrue(r.ease >= 1.3)
    }

    @Test fun sessionRespectsBudgetButNeverEmptyWhenDue() {
        val items = listOf(item(0, 1, 20).copy(id = "x"), item(0, 1, 20).copy(id = "y"))
        // 20 ayat * 25 s = 500 s each; a 10 min budget holds one, not two.
        assertEquals(1, HifzPlanner.session(items, 10, 10).size)
        assertEquals(1, HifzPlanner.session(items, 10, 1).size)
        assertEquals(0, HifzPlanner.session(items.map { it.copy(dueDay = 99) }, 10, 10).size)
    }

    @Test fun daysSince() {
        assertEquals(0, HifzPlanner.daysSince(null, 10))
        assertEquals(4, HifzPlanner.daysSince(6, 10))
    }
}
