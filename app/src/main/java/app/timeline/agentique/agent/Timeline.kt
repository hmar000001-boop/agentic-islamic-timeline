package app.timeline.agentique.agent

import app.timeline.agentique.R
import app.timeline.agentique.core.AppSettings
import app.timeline.agentique.core.GeoPoint
import app.timeline.agentique.core.Geo
import app.timeline.agentique.core.Proactivity
import app.timeline.agentique.core.UiText
import app.timeline.agentique.core.clock
import app.timeline.agentique.core.formatNum
import app.timeline.agentique.prayer.Prayer
import app.timeline.agentique.prayer.PrayerCalc
import app.timeline.agentique.prayer.PrayerTime
import app.timeline.agentique.quran.HifzData
import app.timeline.agentique.quran.HifzPlanner
import app.timeline.agentique.ui.Routes
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.roundToInt

enum class CardKind { SETUP, PRAYER, HIFZ, TRAVEL, ZAKAT, HALAL }

sealed interface Act {
    data class Open(val route: String) : Act
    data class SetHifzMinutes(val minutes: Int) : Act
    data object EnableTravel : Act
}

data class CardAction(val label: UiText, val act: Act)

data class TimelineCard(
    val id: String,
    val kind: CardKind,
    val at: ZonedDateTime?,
    val title: UiText,
    val body: UiText? = null,
    val actions: List<CardAction> = emptyList(),
    val sources: List<UiText> = emptyList(),
    val pinned: Boolean = false,
    val past: Boolean = false,
    val highlight: Boolean = false,
    val dismissible: Boolean = true,
)

data class TimelineInput(
    val now: ZonedDateTime,
    val s: AppSettings,
    val at: GeoPoint?,
    val deviceAt: GeoPoint?,
    val hifz: HifzData,
    val methodLabel: String,
)

/**
 * The "agent": explicit, explainable rules that read the context and propose cards. It never acts
 * on its own; every suggestion is a card the user can open, apply or swipe away (reversible).
 */
object TimelineBuilder {
    private const val AWAY_KM = 50.0

    private fun open(route: String) = CardAction(UiText(R.string.open), Act.Open(route))

    fun build(i: TimelineInput): List<TimelineCard> {
        val s = i.s
        val date = i.now.toLocalDate()
        val today = date.toEpochDay()
        val proactive = s.proactivity != Proactivity.LOW
        val cards = ArrayList<TimelineCard>()

        if (s.madhhab == null) {
            cards += TimelineCard(
                "setup_madhhab", CardKind.SETUP, null,
                UiText(R.string.card_setup_madhhab_title), UiText(R.string.card_setup_madhhab_body),
                actions = listOf(open(Routes.SETTINGS)), pinned = true, dismissible = false,
            )
        }
        if (i.at == null) {
            cards += TimelineCard(
                "setup_location", CardKind.SETUP, null,
                UiText(R.string.card_setup_location_title), UiText(R.string.card_setup_location_body),
                actions = listOf(open(Routes.SETTINGS)), pinned = true, dismissible = false,
            )
        }

        i.at?.let { at ->
            val todays = PrayerCalc.times(date, at, s, i.now.zone).filter { it.prayer != Prayer.SUNRISE }
            val next = PrayerCalc.next(i.now, at, s)
            val extra = next?.takeIf { n -> todays.none { it.prayer == n.prayer && it.time == n.time } }
            val src = listOf(UiText(R.string.source_calc, i.methodLabel), UiText(R.string.source_prayer_note))
            for (pt in todays + listOfNotNull(extra)) {
                val isNext = next != null && pt.prayer == next.prayer && pt.time == next.time
                cards += TimelineCard(
                    "prayer_${pt.time.toLocalDate()}_${pt.prayer}", CardKind.PRAYER, pt.time,
                    UiText(pt.prayer.label), prayerBody(pt, isNext, i.now),
                    sources = src, past = pt.time.isBefore(i.now), highlight = isNext, dismissible = false,
                )
            }
        }

        if (proactive) {
            val due = HifzPlanner.due(i.hifz.items, today)
            if (due.isNotEmpty()) {
                val minutes = HifzPlanner.sessionMinutes(HifzPlanner.session(i.hifz.items, today, s.hifzMinutes))
                cards += TimelineCard(
                    "hifz_due", CardKind.HIFZ, null,
                    UiText(R.string.card_hifz_title), UiText(R.string.card_hifz_due_body, due.size, minutes),
                    actions = listOf(open(Routes.HIFZ)), sources = listOf(UiText(R.string.source_hifz)), pinned = true,
                )
            }
            val gap = HifzPlanner.daysSince(i.hifz.lastReviewDay, today)
            if (gap >= 3 && s.hifzMinutes > 12 && i.hifz.items.isNotEmpty()) {
                cards += TimelineCard(
                    "hifz_missed", CardKind.HIFZ, null,
                    UiText(R.string.card_hifz_missed_title), UiText(R.string.card_hifz_missed_body, 12),
                    actions = listOf(CardAction(UiText(R.string.action_shorten), Act.SetHifzMinutes(12))), pinned = true,
                )
            }

            val away = if (s.home != null && i.deviceAt != null) Geo.distanceKm(s.home, i.deviceAt) else null
            val travelSources = listOf(UiText(R.string.source_user_choice), UiText(R.string.source_unreviewed))
            if (s.travelMode) {
                cards += TimelineCard(
                    "travel_active", CardKind.TRAVEL, null,
                    UiText(R.string.card_travel_active_title), UiText(R.string.card_travel_active_body),
                    actions = listOf(open(Routes.TRAVEL)), sources = travelSources, pinned = true,
                )
            } else if (away != null && away >= AWAY_KM) {
                cards += TimelineCard(
                    "travel_suggest", CardKind.TRAVEL, null,
                    UiText(R.string.card_travel_suggest_title), UiText(R.string.card_travel_suggest_body, away.roundToInt()),
                    actions = listOf(CardAction(UiText(R.string.action_enable_travel), Act.EnableTravel)),
                    sources = travelSources, pinned = true,
                )
            }

            val net = s.zakatNetWealth
            val nisab = s.zakatNisab
            if (net != null && nisab != null && nisab > 0 && net < nisab && net >= 0.9 * nisab) {
                cards += TimelineCard(
                    "zakat_near", CardKind.ZAKAT, null,
                    UiText(R.string.card_zakat_near_title), UiText(R.string.card_zakat_near_body, (net / nisab * 100).toInt()),
                    actions = listOf(open(Routes.ZAKAT)), sources = listOf(UiText(R.string.source_zakat)),
                )
            } else if (s.zakatDue != null && s.zakatDay != null) {
                cards += TimelineCard(
                    "zakat_last", CardKind.ZAKAT, null,
                    UiText(R.string.card_zakat_last_title), UiText(R.string.card_zakat_last_body, formatNum(s.zakatDue)),
                    actions = listOf(open(Routes.ZAKAT)), sources = listOf(UiText(R.string.source_zakat)),
                )
            }
        }

        if (s.proactivity == Proactivity.HIGH) {
            cards += TimelineCard(
                "halal_shortcut", CardKind.HALAL, null,
                UiText(R.string.card_halal_title), UiText(R.string.card_halal_body),
                actions = listOf(open(Routes.HALAL)), sources = listOf(UiText(R.string.source_unreviewed)),
            )
        }

        return cards
            .filterNot { it.dismissible && "$today:${it.id}" in s.dismissed }
            .sortedWith(
                compareBy<TimelineCard> { if (it.pinned) 0 else if (it.at != null) 1 else 2 }
                    .thenBy { it.at?.toEpochSecond() ?: 0L },
            )
    }

    private fun prayerBody(pt: PrayerTime, isNext: Boolean, now: ZonedDateTime): UiText {
        val clock = pt.time.clock()
        if (!isNext) return UiText(R.string.card_prayer_at, clock)
        val minutes = Duration.between(now, pt.time).toMinutes().coerceAtLeast(0)
        return if (minutes < 60) UiText(R.string.card_prayer_next, clock, minutes)
        else UiText(R.string.card_prayer_next_h, clock, minutes / 60, minutes % 60)
    }
}
