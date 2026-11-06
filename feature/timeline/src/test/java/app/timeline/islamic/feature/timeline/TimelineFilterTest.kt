package app.timeline.islamic.feature.timeline

import app.timeline.islamic.core.timeline.TimelineItem
import app.timeline.islamic.core.timeline.TimelineItemOrigin
import app.timeline.islamic.core.timeline.TimelineItemStatus
import app.timeline.islamic.core.timeline.TimelineItemType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineFilterTest {
    private fun item(type: TimelineItemType) = TimelineItem(
        id = type.name, type = type, title = type.name, startsAt = Instant.EPOCH,
        endsAt = null, zoneId = "UTC", origin = TimelineItemOrigin.USER,
        status = if (type == TimelineItemType.PROPOSAL) TimelineItemStatus.PROPOSED else TimelineItemStatus.CONFIRMED,
        sourceVersion = null
    )

    @Test fun eventFilterDoesNotMixTasksOrProposals() {
        val items = listOf(item(TimelineItemType.EVENT), item(TimelineItemType.PRAYER), item(TimelineItemType.TASK), item(TimelineItemType.PROPOSAL))
        val result = items.filter { TimelineFilter.EVENTS.matches(it) }
        assertEquals(listOf(TimelineItemType.EVENT, TimelineItemType.PRAYER), result.map { it.type })
    }
}
