package app.timeline.islamic.core.timeline

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineWindowTest {
    @Test fun ordinaryDayHasTwentyFourHours() {
        val window = TimelineWindow.forDate(LocalDate.parse("2026-02-10"), ZoneId.of("America/Toronto"))
        assertEquals(24L * 60L * 60L, window.untilExclusive.epochSecond - window.fromInclusive.epochSecond)
    }

    @Test fun springDstDayHasTwentyThreeHours() {
        val window = TimelineWindow.forDate(LocalDate.parse("2026-03-08"), ZoneId.of("America/Toronto"))
        assertEquals(23L * 60L * 60L, window.untilExclusive.epochSecond - window.fromInclusive.epochSecond)
    }

    @Test fun fallDstDayHasTwentyFiveHours() {
        val window = TimelineWindow.forDate(LocalDate.parse("2026-11-01"), ZoneId.of("America/Toronto"))
        assertEquals(25L * 60L * 60L, window.untilExclusive.epochSecond - window.fromInclusive.epochSecond)
    }

    @Test fun onlyOrdinaryItemsAreUserCreatable() {
        assertTrue(TimelineItemType.EVENT.isUserCreatable())
        assertTrue(TimelineItemType.TASK.isUserCreatable())
        assertTrue(TimelineItemType.HABIT.isUserCreatable())
        assertEquals(false, TimelineItemType.PROPOSAL.isUserCreatable())
        assertEquals(false, TimelineItemType.PRAYER.isUserCreatable())
    }
}
