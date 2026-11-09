package app.timeline.islamic.feature.hifz

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class HifzReviewPlannerTest {
    @Test fun successfulReviewsProgressAndFailedReviewResets() {
        val passage = QuranPassageReference("mushaf-v1", "quran:2:255", 2, 255, 255)
        val first = HifzReviewPlanner.recordReview(passage, HifzReviewState.NEW, 4, Instant.EPOCH)
        assertEquals(1, first.next.intervalDays)
        val second = HifzReviewPlanner.recordReview(passage, first.next, 5, Instant.EPOCH.plusSeconds(86_400))
        assertEquals(6, second.next.intervalDays)
        val failed = HifzReviewPlanner.recordReview(passage, second.next, 2, Instant.EPOCH.plusSeconds(604_800))
        assertEquals(0, failed.next.repetitions)
        assertEquals(1, failed.next.intervalDays)
    }
}
