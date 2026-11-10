package app.timeline.islamic.core.timeline

import java.time.Instant

/** Only persisted/user-confirmed items belong in this projection. Agent suggestions use PROPOSAL. */
enum class TimelineItemType { PRAYER, EVENT, HABIT, TASK, PROPOSAL, OBSERVATION, HISTORY }
enum class TimelineItemStatus { CONFIRMED, PROPOSED, COMPLETED, DISMISSED, CANCELLED, ARCHIVED }
enum class TimelineItemOrigin { USER, DETERMINISTIC_ENGINE, IMPORTED, AGENT }

data class TimelineItem(
    val id: String,
    val type: TimelineItemType,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant?,
    val zoneId: String,
    val origin: TimelineItemOrigin,
    val status: TimelineItemStatus,
    val sourceVersion: String?
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(zoneId.isNotBlank())
        require(endsAt == null || !endsAt.isBefore(startsAt))
        require(status != TimelineItemStatus.PROPOSED || type == TimelineItemType.PROPOSAL)
    }
}

interface TimelineRepository {
    fun observeItems(fromInclusive: Instant, untilExclusive: Instant): kotlinx.coroutines.flow.Flow<List<TimelineItem>>
    suspend fun upsert(item: TimelineItem)
    suspend fun remove(itemId: String)
}


/** Date boundaries are computed in the user's zone so DST days can be 23 or 25 hours. */
data class TimelineWindow(val fromInclusive: Instant, val untilExclusive: Instant) {
    init { require(untilExclusive.isAfter(fromInclusive)) }

    companion object {
        fun forDate(date: java.time.LocalDate, zone: java.time.ZoneId): TimelineWindow =
            TimelineWindow(
                date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant()
            )
    }
}

/** Only ordinary user-owned items can be created directly from the Timeline form. */
fun TimelineItemType.isUserCreatable(): Boolean =
    this == TimelineItemType.EVENT || this == TimelineItemType.TASK || this == TimelineItemType.HABIT
