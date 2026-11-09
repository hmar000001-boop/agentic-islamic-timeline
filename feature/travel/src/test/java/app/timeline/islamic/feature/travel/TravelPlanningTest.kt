package app.timeline.islamic.feature.travel

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class TravelPlanningTest {
    @Test fun torontoQiblaBearingPointsNorthEast() {
        val bearing = QiblaCalculator.calculate(43.6532, -79.3832).degreesFromTrueNorthClockwise
        assertTrue("bearing=$bearing", bearing in 40.0..70.0)
    }

    @Test fun dstGapIsRejectedAndOverlapRequiresExplicitOffset() {
        assertThrows(IllegalArgumentException::class.java) {
            TravelSegment.resolveLocal(LocalDateTime.of(2026, 3, 8, 2, 30), "America/Toronto")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TravelSegment.resolveLocal(LocalDateTime.of(2026, 11, 1, 1, 30), "America/Toronto")
        }
        assertNotNull(TravelSegment.resolveLocal(LocalDateTime.of(2026, 11, 1, 1, 30), "America/Toronto", ZoneOffset.ofHours(-4)))
    }

    @Test fun qasrJamGuidanceDoesNotInferFromZoneChange() {
        assertEquals(TravelFiqhGuidanceStatus.EVIDENCE_AND_MADHHAB_REQUIRED,
            TravelFiqhGuidanceResolver.resolve(null, emptyList()).status)
    }

    @Test fun suppliedEvidenceDoesNotPretendAnApprovedRulesetIsInstalled() {
        assertEquals(TravelFiqhGuidanceStatus.NO_APPROVED_RULESET,
            TravelFiqhGuidanceResolver.resolve("MALIKI", listOf("user:note-1")).status)
    }
}
