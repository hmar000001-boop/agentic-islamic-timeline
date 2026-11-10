package app.timeline.islamic.feature.travel

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.zone.ZoneRules
import kotlin.math.*

data class TravelStop(val label: String, val latitude: Double?, val longitude: Double?, val zoneId: String) {
    init {
        require(label.isNotBlank() && label.length <= 160)
        ZoneId.of(zoneId)
        require((latitude == null) == (longitude == null))
        if (latitude != null && longitude != null) {
            require(latitude.isFinite() && latitude in -90.0..90.0)
            require(longitude.isFinite() && longitude in -180.0..180.0)
        }
    }
}

data class TravelSegment(
    val origin: TravelStop,
    val destination: TravelStop,
    val departureInstant: Instant,
    val arrivalInstant: Instant,
    val sourceReference: String,
    val capturedAt: Instant
) {
    init {
        require(arrivalInstant.isAfter(departureInstant))
        require(sourceReference.isNotBlank() && sourceReference.length <= 512)
    }

    val originLocalDeparture get() = departureInstant.atZone(ZoneId.of(origin.zoneId))
    val destinationLocalArrival get() = arrivalInstant.atZone(ZoneId.of(destination.zoneId))

    companion object {
        /** Reject DST gaps and require an explicit offset for an ambiguous wall-clock time. */
        fun resolveLocal(local: LocalDateTime, zoneId: String, explicitOffset: ZoneOffset? = null): Instant {
            val zone = ZoneId.of(zoneId)
            val offsets = zone.rules.getValidOffsets(local)
            require(offsets.isNotEmpty()) { "Local time falls in a daylight-saving gap" }
            val offset = when {
                offsets.size == 1 -> offsets.single().also { require(explicitOffset == null || explicitOffset == it) }
                else -> explicitOffset?.also { require(it in offsets) }
                    ?: throw IllegalArgumentException("Ambiguous local time: choose one of ${offsets.joinToString()}")
            }
            return local.toInstant(offset)
        }
    }
}

data class TravelPlan(
    val id: String,
    val segments: List<TravelSegment>,
    val importedAt: Instant,
    val sourceVersion: String
) {
    init {
        require(id.isNotBlank() && sourceVersion.isNotBlank())
        require(segments.isNotEmpty() && segments.size <= 200)
        segments.zipWithNext().forEach { (a, b) ->
            require(!b.departureInstant.isBefore(a.arrivalInstant)) { "Travel segments overlap or are out of order" }
        }
    }
}

data class QiblaBearing(val degreesFromTrueNorthClockwise: Double, val calculatedForLatitude: Double, val calculatedForLongitude: Double)

object QiblaCalculator {
    private const val KAABA_LATITUDE = 21.422487
    private const val KAABA_LONGITUDE = 39.826206

    fun calculate(latitude: Double, longitude: Double): QiblaBearing {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        val lat = Math.toRadians(latitude)
        val kaabaLat = Math.toRadians(KAABA_LATITUDE)
        val deltaLon = Math.toRadians(KAABA_LONGITUDE - longitude)
        val y = sin(deltaLon)
        val x = cos(lat) * tan(kaabaLat) - sin(lat) * cos(deltaLon)
        val bearing = ((Math.toDegrees(atan2(y, x)) % 360.0) + 360.0) % 360.0
        return QiblaBearing(bearing, latitude, longitude)
    }
}

enum class TravelFiqhGuidanceStatus { EVIDENCE_AND_MADHHAB_REQUIRED, NO_APPROVED_RULESET }
data class TravelFiqhGuidance(val status: TravelFiqhGuidanceStatus, val madhhab: String?, val evidenceKeys: List<String>)

/** Travel dates/time zones are deterministic; qasr/jam' is never inferred from time-zone change or distance alone. */
object TravelFiqhGuidanceResolver {
    /**
     * No approved travel-fiqh ruleset registry is shipped in this tranche. A caller-provided Boolean
     * must never elevate user-entered evidence into an approved ruling, so this API intentionally
     * exposes no `hasApprovedRuleset` switch.
     */
    fun resolve(madhhab: String?, evidenceKeys: List<String>): TravelFiqhGuidance {
        val validMadhhab = madhhab in setOf("HANAFI", "MALIKI", "SHAFII", "HANBALI")
        return when {
            !validMadhhab || evidenceKeys.isEmpty() -> TravelFiqhGuidance(
                TravelFiqhGuidanceStatus.EVIDENCE_AND_MADHHAB_REQUIRED, madhhab, emptyList()
            )
            else -> TravelFiqhGuidance(TravelFiqhGuidanceStatus.NO_APPROVED_RULESET, madhhab, evidenceKeys.distinct())
        }
    }
}
