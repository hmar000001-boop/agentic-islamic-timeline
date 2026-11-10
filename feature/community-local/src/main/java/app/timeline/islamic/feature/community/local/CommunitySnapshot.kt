package app.timeline.islamic.feature.community.local

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

const val COMMUNITY_SNAPSHOT_V1 = "TL-COMMUNITY-V1"

data class CommunityEntry(
    val id: String,
    val title: String,
    val category: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val zoneId: String,
    val locationLabel: String,
    val sourceName: String,
    val sourceVersion: String,
    val sourceReference: String
) {
    init {
        require(id.isNotBlank() && id.length <= 96)
        require(title.isNotBlank() && title.length <= 200)
        require(category in setOf("PRAYER", "STUDY", "FAMILY", "VOLUNTEER", "EDUCATION", "OTHER"))
        require(endsAt.isAfter(startsAt))
        ZoneId.of(zoneId)
        require(locationLabel.isNotBlank() && locationLabel.length <= 200)
        require(sourceName.isNotBlank() && sourceName.length <= 160)
        require(sourceVersion.isNotBlank() && sourceVersion.length <= 64)
        require(sourceReference.isNotBlank() && sourceReference.length <= 512)
    }
}

data class CommunitySnapshot(
    val sourceId: String,
    val sourceVersion: String,
    val capturedAt: Instant,
    val entries: List<CommunityEntry>
) {
    init {
        require(sourceId.isNotBlank() && sourceId.length <= 128)
        require(sourceVersion.isNotBlank() && sourceVersion.length <= 64)
        require(entries.size <= 2_000)
        require(entries.map { it.id }.distinct().size == entries.size)
        require(entries.all { it.sourceVersion == sourceVersion })
    }

    fun freshness(asOf: Instant, maxAge: Duration): SnapshotFreshness {
        require(!maxAge.isNegative && !maxAge.isZero)
        val age = Duration.between(capturedAt, asOf)
        return when {
            age.isNegative -> SnapshotFreshness.CLOCK_OR_CAPTURE_TIME_INVALID
            age > maxAge -> SnapshotFreshness.STALE
            else -> SnapshotFreshness.CURRENT_AT_CAPTURE
        }
    }
}

enum class SnapshotFreshness { CURRENT_AT_CAPTURE, STALE, CLOCK_OR_CAPTURE_TIME_INVALID }

data class CommunityImportResult(val snapshot: CommunitySnapshot, val warnings: List<String>)

/** Strict offline import format. It never fetches remote updates or implies live presence. */
object CommunitySnapshotImporter {
    private const val MAX_INPUT_BYTES = 512 * 1024
    private const val MAX_LINES = 2_001

    fun parse(text: String, capturedAt: Instant): CommunityImportResult {
        require(text.toByteArray(Charsets.UTF_8).size in 1..MAX_INPUT_BYTES)
        val lines = text.lineSequence().map(String::trimEnd).filter(String::isNotBlank).toList()
        require(lines.size in 2..MAX_LINES)
        require(lines.first() == COMMUNITY_SNAPSHOT_V1) { "Unsupported community snapshot format" }
        // Header: sourceId|sourceVersion|sourceCapturedAtISO. Import time must not
        // masquerade as source freshness; the source's capture instant is explicit.
        val header = lines[1].split('|')
        require(header.size == 3 && header.all(String::isNotBlank))
        val sourceCapturedAt = Instant.parse(header[2])
        val entries = lines.drop(2).map { line ->
            val columns = line.split('|')
            require(columns.size == 10) { "Each community row must contain exactly ten pipe-separated fields" }
            require(columns[8] == header[1]) { "Community row source version does not match the snapshot header" }
            CommunityEntry(
                id = columns[0], title = columns[1], category = columns[2],
                startsAt = Instant.parse(columns[3]), endsAt = Instant.parse(columns[4]), zoneId = columns[5],
                locationLabel = columns[6], sourceName = columns[7], sourceVersion = columns[8], sourceReference = columns[9]
            )
        }
        require(entries.isNotEmpty()) { "Community snapshot must contain at least one row" }
        val snapshot = CommunitySnapshot(header[0], header[1], sourceCapturedAt, entries)
        val warnings = mutableListOf("IMPORTED_SNAPSHOT_ONLY", "NO_LIVE_UPDATES", "VERIFY_EVENT_DETAILS_WITH_SOURCE")
        if (sourceCapturedAt.isAfter(capturedAt)) warnings += "SOURCE_CAPTURE_TIME_IN_FUTURE"
        return CommunityImportResult(snapshot, warnings)
    }
}
