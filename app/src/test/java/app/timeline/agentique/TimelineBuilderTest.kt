package app.timeline.agentique

import app.timeline.agentique.agent.CardKind
import app.timeline.agentique.agent.TimelineBuilder
import app.timeline.agentique.agent.TimelineInput
import app.timeline.agentique.core.AppSettings
import app.timeline.agentique.core.Madhhab
import app.timeline.agentique.core.Proactivity
import app.timeline.agentique.quran.HifzData
import app.timeline.agentique.quran.HifzItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimelineBuilderTest {
    private val now = ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, ZoneId.of("UTC"))

    private fun input(s: AppSettings, hifz: HifzData = HifzData()) =
        TimelineInput(now, s, at = null, deviceAt = null, hifz = hifz, methodLabel = "")

    @Test fun missingMadhhabAndLocationProduceSetupCards() {
        val ids = TimelineBuilder.build(input(AppSettings())).map { it.id }
        assertTrue("setup_madhhab" in ids && "setup_location" in ids)
    }

    @Test fun noInventedMadhhabWhenChosen() {
        val ids = TimelineBuilder.build(input(AppSettings(madhhab = Madhhab.SHAFII))).map { it.id }
        assertFalse("setup_madhhab" in ids)
    }

    @Test fun dueHifzSuggestsASessionUnlessQuiet() {
        val today = now.toLocalDate().toEpochDay()
        val hifz = HifzData(items = listOf(HifzItem("a", 2, 1, 5, dueDay = today)), lastReviewDay = today)
        val normal = TimelineBuilder.build(input(AppSettings(madhhab = Madhhab.SHAFII), hifz))
        assertTrue(normal.any { it.kind == CardKind.HIFZ })
        val quiet = TimelineBuilder.build(input(AppSettings(madhhab = Madhhab.SHAFII, proactivity = Proactivity.LOW), hifz))
        assertFalse(quiet.any { it.kind == CardKind.HIFZ })
    }

    @Test fun missedDaysSuggestAShorterSession() {
        val today = now.toLocalDate().toEpochDay()
        val hifz = HifzData(items = listOf(HifzItem("a", 2, 1, 5, dueDay = today + 5)), lastReviewDay = today - 4)
        val cards = TimelineBuilder.build(input(AppSettings(madhhab = Madhhab.SHAFII, hifzMinutes = 20), hifz))
        assertEquals(1, cards.count { it.id == "hifz_missed" })
    }

    @Test fun dismissedCardsStayHiddenForTheDay() {
        val today = now.toLocalDate().toEpochDay()
        val s = AppSettings(madhhab = Madhhab.SHAFII, proactivity = Proactivity.HIGH, dismissed = setOf("$today:halal_shortcut"))
        assertFalse(TimelineBuilder.build(input(s)).any { it.id == "halal_shortcut" })
    }
}
