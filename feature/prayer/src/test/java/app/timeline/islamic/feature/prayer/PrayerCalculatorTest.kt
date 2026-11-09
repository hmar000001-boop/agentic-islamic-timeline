package app.timeline.islamic.feature.prayer

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PrayerCalculatorTest {
    @Test fun torontoCustomProfileProducesOrderedLocalSolarEvents() {
        val profile = PrayerCalculationProfile("test", "Test Toronto", 43.6532, -79.3832,
            "America/Toronto", 15.0, 15.0, 1.0, HighLatitudeRule.MIDDLE_OF_NIGHT)
        val result = PrayerCalculator.calculate(LocalDate.of(2026, 11, 9), profile)
        val times = listOf(result.fajr, result.sunrise, result.dhuhr, result.asr, result.maghrib, result.isha)
        assertTrue(times.all { it != null })
        assertTrue(times.zipWithNext().all { (a, b) -> a!!.isBefore(b!!) })
    }

    @Test fun usesElapsedInstantDurationAcrossSpringDstForHighLatitudeFallback() {
        val profile = PrayerCalculationProfile("dst-high-lat", "DST regression", 80.0, -75.0,
            "America/Toronto", 18.0, 18.0, 1.0, HighLatitudeRule.ONE_SEVENTH)
        val previous = PrayerCalculator.calculate(LocalDate.of(2026, 3, 7), profile)
        val transitionDay = PrayerCalculator.calculate(LocalDate.of(2026, 3, 8), profile)
        assertTrue(transitionDay.highLatitudeFallbackApplied)
        val previousSunset = previous.maghrib!!.atZone(ZoneId.of(profile.zoneId)).toInstant()
        val sunrise = transitionDay.sunrise!!.atZone(ZoneId.of(profile.zoneId)).toInstant()
        val elapsedMinutes = java.time.Duration.between(previousSunset, sunrise).toMinutes()
        val wallClockMinutes = java.time.Duration.between(previous.maghrib, transitionDay.sunrise).toMinutes()
        assertTrue("The test interval must cross the spring DST jump", elapsedMinutes != wallClockMinutes)
        val expectedFajr = transitionDay.sunrise!!.minusMinutes(kotlin.math.round(elapsedMinutes / 7.0).toLong())
        assertEquals(expectedFajr, transitionDay.fajr)
    }

    @Test fun profileRequiresExplicitValidTimezoneAndCoordinates() {
        assertThrows(IllegalArgumentException::class.java) {
            PrayerCalculationProfile("x", "x", 91.0, 0.0, "UTC", 18.0, 17.0, 1.0, HighLatitudeRule.NONE)
        }
    }
}
