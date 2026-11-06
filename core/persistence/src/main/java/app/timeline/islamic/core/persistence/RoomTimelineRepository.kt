package app.timeline.islamic.core.persistence

import app.timeline.islamic.core.timeline.*
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTimelineRepository(private val dao: TimelineDao) : TimelineRepository {
    override fun observeItems(fromInclusive: Instant, untilExclusive: Instant): Flow<List<TimelineItem>> =
        dao.observeBetween(fromInclusive.toEpochMilli(), untilExclusive.toEpochMilli()).map { rows -> rows.map(::toDomain) }

    override suspend fun upsert(item: TimelineItem) = dao.upsert(item.toEntity())
    override suspend fun remove(itemId: String) = dao.deleteById(itemId)

    private fun toDomain(row: TimelineItemEntity) = TimelineItem(
        id = row.id,
        type = TimelineItemType.valueOf(row.type),
        title = row.title,
        startsAt = Instant.ofEpochMilli(row.startsAtEpochMillis),
        endsAt = row.endsAtEpochMillis?.let(Instant::ofEpochMilli),
        zoneId = row.zoneId,
        origin = TimelineItemOrigin.valueOf(row.origin),
        status = TimelineItemStatus.valueOf(row.status),
        sourceVersion = row.sourceVersion
    )

    private fun TimelineItem.toEntity() = TimelineItemEntity(
        id, type.name, title, startsAt.toEpochMilli(), endsAt?.toEpochMilli(), zoneId,
        origin.name, status.name, sourceVersion
    )
}
