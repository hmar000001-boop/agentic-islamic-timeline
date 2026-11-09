package app.timeline.islamic.feature.hifz

import java.time.Instant
import java.time.temporal.ChronoUnit

/** Reference to an exact verse identity; it never embeds or invents Quran text. */
data class QuranPassageReference(
    val mushafEditionId: String,
    val stableEvidenceKey: String,
    val surahNumber: Int,
    val firstAyah: Int,
    val lastAyah: Int
) {
    init {
        require(mushafEditionId.isNotBlank() && mushafEditionId.length <= 128)
        require(stableEvidenceKey.isNotBlank() && stableEvidenceKey.length <= 256)
        require(surahNumber in 1..114)
        require(firstAyah > 0 && lastAyah >= firstAyah)
    }
}

data class HifzPlan(
    val id: String,
    val mushafEditionId: String,
    val planVersion: Long,
    val passages: List<QuranPassageReference>,
    val createdAt: Instant
) {
    init {
        require(id.isNotBlank() && mushafEditionId.isNotBlank() && planVersion > 0)
        require(passages.isNotEmpty() && passages.size <= 5_000)
        require(passages.all { it.mushafEditionId == mushafEditionId })
        require(passages.map { it.stableEvidenceKey }.distinct().size == passages.size)
    }
}

data class HifzReviewState(val repetitions: Int, val intervalDays: Int, val easeFactor: Double) {
    init {
        require(repetitions >= 0)
        require(intervalDays in 0..36_500)
        require(easeFactor.isFinite() && easeFactor in 1.3..3.0)
    }

    companion object { val NEW = HifzReviewState(0, 0, 2.5) }
}

data class HifzReviewResult(
    val passage: QuranPassageReference,
    val quality: Int,
    val previous: HifzReviewState,
    val next: HifzReviewState,
    val reviewedAt: Instant,
    val nextDueAt: Instant,
    val algorithmId: String = "SM2-LOCAL-V1"
)

/**
 * User-rated spaced repetition only. It does not score recitation, tajwid, correctness of memorization,
 * or religious merit. The user supplies quality after reviewing the exact Quran passage.
 */
object HifzReviewPlanner {
    fun recordReview(
        passage: QuranPassageReference,
        previous: HifzReviewState,
        quality: Int,
        reviewedAt: Instant
    ): HifzReviewResult {
        require(quality in 0..5) { "Quality must be the user's explicit 0..5 assessment" }
        val nextEase = (previous.easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))
            .coerceIn(1.3, 3.0)
        val next = if (quality < 3) {
            HifzReviewState(repetitions = 0, intervalDays = 1, easeFactor = nextEase)
        } else {
            val repetitions = previous.repetitions + 1
            val interval = when (repetitions) {
                1 -> 1
                2 -> 6
                else -> (previous.intervalDays.coerceAtLeast(6) * nextEase).roundToIntSaturated()
            }.coerceIn(1, 36_500)
            HifzReviewState(repetitions, interval, nextEase)
        }
        return HifzReviewResult(passage, quality, previous, next, reviewedAt,
            reviewedAt.plus(next.intervalDays.toLong(), ChronoUnit.DAYS))
    }

    private fun Double.roundToIntSaturated(): Int = kotlin.math.round(this).toLong().coerceIn(1, 36_500).toInt()
}
