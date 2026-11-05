package app.timeline.agentique.prayer

import androidx.annotation.StringRes
import app.timeline.agentique.R
import app.timeline.agentique.core.AppSettings
import app.timeline.agentique.core.GeoPoint
import app.timeline.agentique.core.Madhhab
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.Qibla
import com.batoulapps.adhan.data.DateComponents
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

enum class Prayer(@StringRes val label: Int) {
    FAJR(R.string.prayer_fajr),
    SUNRISE(R.string.prayer_sunrise),
    DHUHR(R.string.prayer_dhuhr),
    ASR(R.string.prayer_asr),
    MAGHRIB(R.string.prayer_maghrib),
    ISHA(R.string.prayer_isha),
}

data class PrayerTime(val prayer: Prayer, val time: ZonedDateTime)

/** Deterministic prayer times: the language model is never involved here. */
object PrayerCalc {
    fun times(date: LocalDate, at: GeoPoint, s: AppSettings, zone: ZoneId): List<PrayerTime> {
        val method = runCatching { CalculationMethod.valueOf(s.method) }.getOrDefault(CalculationMethod.MUSLIM_WORLD_LEAGUE)
        val params = method.parameters
        params.madhab = if (s.madhhab == Madhhab.HANAFI) Madhab.HANAFI else Madhab.SHAFI
        val pt = PrayerTimes(
            Coordinates(at.lat, at.lon),
            DateComponents(date.year, date.monthValue, date.dayOfMonth),
            params,
        )
        fun z(d: Date): ZonedDateTime = d.toInstant().atZone(zone)
        return listOf(
            PrayerTime(Prayer.FAJR, z(pt.fajr)),
            PrayerTime(Prayer.SUNRISE, z(pt.sunrise)),
            PrayerTime(Prayer.DHUHR, z(pt.dhuhr)),
            PrayerTime(Prayer.ASR, z(pt.asr)),
            PrayerTime(Prayer.MAGHRIB, z(pt.maghrib)),
            PrayerTime(Prayer.ISHA, z(pt.isha)),
        )
    }

    /** Next of the five daily prayers (sunrise excluded), looking at today then tomorrow. */
    fun next(now: ZonedDateTime, at: GeoPoint, s: AppSettings): PrayerTime? {
        for (offset in 0L..1L) {
            val list = times(now.toLocalDate().plusDays(offset), at, s, now.zone).filter { it.prayer != Prayer.SUNRISE }
            list.firstOrNull { it.time.isAfter(now) }?.let { return it }
        }
        return null
    }

    fun qiblaDegrees(at: GeoPoint): Double = Qibla(Coordinates(at.lat, at.lon)).direction
}
