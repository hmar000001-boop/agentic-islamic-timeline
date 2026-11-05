package app.timeline.agentique.prayer

import app.timeline.agentique.core.Madhhab

enum class QasrStatus { NOT_TRAVELLER, OBLIGATORY, PERMISSIBLE }
enum class JamStatus { NOT_TRAVELLER, NOT_PERMITTED, PERMITTED_WITH_CONDITIONS }

data class TravelRuling(
    val qasr: QasrStatus,
    val jam: JamStatus,
    val maxStayDays: Int,
    val stayExceeded: Boolean,
)

/**
 * Deterministic, madhhab-driven rules. NOT scholar-reviewed: the app shows this disclaimer next to
 * every result. Thresholds are user-adjustable (settings) because they differ between scholars.
 */
object TravelRules {
    /** Longest intended stay (days) that still keeps traveller status. Hanafi: resident at 15+ days. */
    fun maxStayDays(m: Madhhab): Int = if (m == Madhhab.HANAFI) 14 else 4

    fun evaluate(m: Madhhab, distanceKm: Double, thresholdKm: Double, stayDays: Int): TravelRuling {
        val maxStay = maxStayDays(m)
        val exceeded = stayDays > maxStay
        val traveller = distanceKm >= thresholdKm && !exceeded
        return TravelRuling(
            qasr = when {
                !traveller -> QasrStatus.NOT_TRAVELLER
                m == Madhhab.HANAFI -> QasrStatus.OBLIGATORY
                else -> QasrStatus.PERMISSIBLE
            },
            jam = when {
                !traveller -> JamStatus.NOT_TRAVELLER
                m == Madhhab.HANAFI -> JamStatus.NOT_PERMITTED
                else -> JamStatus.PERMITTED_WITH_CONDITIONS
            },
            maxStayDays = maxStay,
            stayExceeded = exceeded,
        )
    }
}
