package app.timeline.islamic.feature.prayer

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.*

/** A user-selected, explicitly parameterized profile. No calculation method is silently selected. */
data class PrayerCalculationProfile(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    val fajrDepressionDegrees: Double,
    val ishaDepressionDegrees: Double,
    val asrShadowFactor: Double,
    val highLatitudeRule: HighLatitudeRule,
    val fajrAdjustmentMinutes: Int = 0,
    val sunriseAdjustmentMinutes: Int = 0,
    val dhuhrAdjustmentMinutes: Int = 0,
    val asrAdjustmentMinutes: Int = 0,
    val maghribAdjustmentMinutes: Int = 0,
    val ishaAdjustmentMinutes: Int = 0
) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        ZoneId.of(zoneId)
        require(fajrDepressionDegrees.isFinite() && fajrDepressionDegrees in 0.1..30.0)
        require(ishaDepressionDegrees.isFinite() && ishaDepressionDegrees in 0.1..30.0)
        require(asrShadowFactor.isFinite() && asrShadowFactor in 1.0..2.0)
        listOf(fajrAdjustmentMinutes, sunriseAdjustmentMinutes, dhuhrAdjustmentMinutes,
            asrAdjustmentMinutes, maghribAdjustmentMinutes, ishaAdjustmentMinutes).forEach {
            require(it in -60..60)
        }
    }
}

enum class HighLatitudeRule { NONE, MIDDLE_OF_NIGHT, ONE_SEVENTH, ANGLE_BASED }

data class PrayerSchedule(
    val localDate: LocalDate,
    val zoneId: String,
    val fajr: LocalDateTime?,
    val sunrise: LocalDateTime?,
    val dhuhr: LocalDateTime?,
    val asr: LocalDateTime?,
    val maghrib: LocalDateTime?,
    val isha: LocalDateTime?,
    val highLatitudeFallbackApplied: Boolean,
    val warnings: List<String>
)

/** Solar-event calculation only. It does not decide whether a prayer is valid or provide fiqh rulings. */
object PrayerCalculator {
    private const val SUNRISE_ALTITUDE_DEGREES = -0.833
    private const val MINUTES_PER_DEGREE = 4.0

    fun calculate(date: LocalDate, profile: PrayerCalculationProfile): PrayerSchedule {
        val zone = ZoneId.of(profile.zoneId)
        val solar = solar(date, profile.latitude, profile.longitude, zone)
        val warnings = mutableListOf<String>()
        val sunriseHour = hourAngle(solar, profile.latitude, SUNRISE_ALTITUDE_DEGREES)
        val sunsetHour = hourAngle(solar, profile.latitude, SUNRISE_ALTITUDE_DEGREES)
        if (sunriseHour == null || sunsetHour == null) {
            return PrayerSchedule(date, profile.zoneId, null, null, null, null, null, null, false,
                listOf("SUNRISE_SUNSET_UNAVAILABLE_AT_THIS_LATITUDE"))
        }

        val sunrise = localTime(date, solar.solarNoonHours - sunriseHour + profile.sunriseAdjustmentMinutes / 60.0)
        val sunset = localTime(date, solar.solarNoonHours + sunsetHour + profile.maghribAdjustmentMinutes / 60.0)
        val fajrRaw = hourAngle(solar, profile.latitude, -profile.fajrDepressionDegrees)
        val ishaRaw = hourAngle(solar, profile.latitude, -profile.ishaDepressionDegrees)
        var fajr = fajrRaw?.let { localTime(date, solar.solarNoonHours - it + profile.fajrAdjustmentMinutes / 60.0) }
        var isha = ishaRaw?.let { localTime(date, solar.solarNoonHours + it + profile.ishaAdjustmentMinutes / 60.0) }
        var fallback = false

        if (fajr == null || isha == null) {
            when (profile.highLatitudeRule) {
                HighLatitudeRule.NONE -> warnings += "TWILIGHT_UNAVAILABLE_HIGH_LATITUDE_RULE_NONE"
                else -> {
                    fun nightFraction(angle: Double): Double = when (profile.highLatitudeRule) {
                        HighLatitudeRule.MIDDLE_OF_NIGHT -> 0.5
                        HighLatitudeRule.ONE_SEVENTH -> 1.0 / 7.0
                        HighLatitudeRule.ANGLE_BASED -> (angle / 60.0).coerceIn(0.0, 0.5)
                        HighLatitudeRule.NONE -> error("unreachable")
                    }
                    if (fajr == null) {
                        val previousSunset = solar(date.minusDays(1), profile.latitude, profile.longitude, zone).let { prev ->
                            hourAngle(prev, profile.latitude, SUNRISE_ALTITUDE_DEGREES)?.let { localTime(date.minusDays(1), prev.solarNoonHours + it) }
                        }
                        if (previousSunset != null) {
                            // Use elapsed instants, not wall-clock LocalDateTime duration: a DST
                            // transition can occur during the night and change its real length.
                            val nightMinutes = java.time.Duration.between(
                                previousSunset.atZone(zone).toInstant(), sunrise.atZone(zone).toInstant()
                            ).toMinutes()
                            fajr = sunrise.minusMinutes((nightMinutes * nightFraction(profile.fajrDepressionDegrees)).roundToLong())
                                .plusMinutes(profile.fajrAdjustmentMinutes.toLong())
                        } else warnings += "FAJR_HIGH_LATITUDE_FALLBACK_UNAVAILABLE"
                    }
                    if (isha == null) {
                        val nextSunrise = solar(date.plusDays(1), profile.latitude, profile.longitude, zone).let { next ->
                            hourAngle(next, profile.latitude, SUNRISE_ALTITUDE_DEGREES)?.let { localTime(date.plusDays(1), next.solarNoonHours - it) }
                        }
                        if (nextSunrise != null) {
                            val nightMinutes = java.time.Duration.between(
                                sunset.atZone(zone).toInstant(), nextSunrise.atZone(zone).toInstant()
                            ).toMinutes()
                            isha = sunset.plusMinutes((nightMinutes * nightFraction(profile.ishaDepressionDegrees)).roundToLong())
                                .plusMinutes(profile.ishaAdjustmentMinutes.toLong())
                        } else warnings += "ISHA_HIGH_LATITUDE_FALLBACK_UNAVAILABLE"
                    }
                    fallback = fajr != null || isha != null
                    if (fallback) warnings += "HIGH_LATITUDE_FALLBACK_APPLIED"
                }
            }
        }

        val dhuhr = localTime(date, solar.solarNoonHours + profile.dhuhrAdjustmentMinutes / 60.0)
        val declination = solar.declinationRadians
        val latitude = Math.toRadians(profile.latitude)
        val shadowAltitude = atan(1.0 / (profile.asrShadowFactor + tan(abs(latitude - declination))))
        val asrHourAngle = hourAngle(solar, profile.latitude, Math.toDegrees(shadowAltitude))
        val asr = asrHourAngle?.let { localTime(date, solar.solarNoonHours + it + profile.asrAdjustmentMinutes / 60.0) }
        if (asr == null) warnings += "ASR_UNAVAILABLE_AT_THIS_LATITUDE"
        return PrayerSchedule(date, profile.zoneId, fajr, sunrise, dhuhr, asr, sunset, isha, fallback, warnings.distinct())
    }

    private data class SolarState(val declinationRadians: Double, val solarNoonHours: Double)

    private fun solar(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SolarState {
        // Calculate at local noon to select the correct civil UTC offset across DST transitions.
        val noonInstant = date.atTime(12, 0).atZone(zone).toInstant()
        val jd = noonInstant.epochSecond / 86_400.0 + 2_440_587.5
        val d = jd - 2_451_545.0
        val g = Math.toRadians(normalizeDegrees(357.529 + 0.98560028 * d))
        val q = normalizeDegrees(280.459 + 0.98564736 * d)
        val meanLongitude = Math.toRadians(normalizeDegrees(q + 1.915 * sin(g) + 0.020 * sin(2 * g)))
        val obliquity = Math.toRadians(23.439 - 0.00000036 * d)
        val rightAscension = atan2(cos(obliquity) * sin(meanLongitude), cos(meanLongitude))
        val rightAscensionHours = normalizeHours(Math.toDegrees(rightAscension) / 15.0)
        val equationOfTimeHours = normalizeSignedHours(q / 15.0 - rightAscensionHours)
        val declination = asin(sin(obliquity) * sin(meanLongitude))
        val offsetHours = date.atTime(12, 0).atZone(zone).offset.totalSeconds / 3600.0
        val solarNoon = 12.0 + offsetHours - longitude / 15.0 - equationOfTimeHours
        require(latitude.isFinite())
        return SolarState(declination, solarNoon)
    }

    private fun hourAngle(solar: SolarState, latitudeDegrees: Double, altitudeDegrees: Double): Double? {
        val latitude = Math.toRadians(latitudeDegrees)
        val altitude = Math.toRadians(altitudeDegrees)
        val denominator = cos(latitude) * cos(solar.declinationRadians)
        if (abs(denominator) < 1e-12) return null
        val cosH = (sin(altitude) - sin(latitude) * sin(solar.declinationRadians)) / denominator
        if (!cosH.isFinite() || cosH !in -1.0..1.0) return null
        return Math.toDegrees(acos(cosH)) / 15.0
    }

    private fun localTime(date: LocalDate, hours: Double): LocalDateTime {
        val totalNanos = (hours * 3_600_000_000_000.0).roundToLong()
        return date.atStartOfDay().plusNanos(totalNanos)
    }

    private fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
    private fun normalizeHours(value: Double): Double = ((value % 24.0) + 24.0) % 24.0
    private fun normalizeSignedHours(value: Double): Double {
        var normalized = value
        while (normalized > 12.0) normalized -= 24.0
        while (normalized < -12.0) normalized += 24.0
        return normalized
    }
}
