package app.timeline.islamic.feature.community.local

import java.time.Instant
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test

class CommunitySnapshotTest {
    @Test fun parsesVersionedLocalSnapshotAndDisplaysItsCaptureFreshness() {
        val content = """$COMMUNITY_SNAPSHOT_V1
mosque-x|2026-1|2026-11-08T12:00:00Z
entry-1|Study circle|STUDY|2026-11-10T18:00:00Z|2026-11-10T19:00:00Z|America/Toronto|Local centre|Mosque|2026-1|local:source"""
        val result = CommunitySnapshotImporter.parse(content, Instant.parse("2026-11-09T12:00:00Z"))
        assertEquals(1, result.snapshot.entries.size)
        assertEquals(SnapshotFreshness.CURRENT_AT_CAPTURE, result.snapshot.freshness(Instant.parse("2026-11-10T12:00:00Z"), Duration.ofDays(30)))
    }

    @Test fun sourceCaptureTimeIsNotReplacedByImportTimeAndFutureCaptureIsFlagged() {
        val content = """$COMMUNITY_SNAPSHOT_V1
mosque-x|2026-1|2026-11-10T12:00:00Z
entry-1|Study circle|STUDY|2026-11-10T18:00:00Z|2026-11-10T19:00:00Z|America/Toronto|Local centre|Mosque|2026-1|local:source"""
        val importedAt = Instant.parse("2026-11-09T12:00:00Z")
        val result = CommunitySnapshotImporter.parse(content, importedAt)
        assertEquals(Instant.parse("2026-11-10T12:00:00Z"), result.snapshot.capturedAt)
        assertTrue(result.warnings.contains("SOURCE_CAPTURE_TIME_IN_FUTURE"))
        assertEquals(SnapshotFreshness.CLOCK_OR_CAPTURE_TIME_INVALID,
            result.snapshot.freshness(importedAt, Duration.ofDays(30)))
    }

    @Test fun rejectsSourceVersionMismatch() {
        val content = """$COMMUNITY_SNAPSHOT_V1
mosque-x|2026-1|2026-11-08T12:00:00Z
entry-1|Study circle|STUDY|2026-11-10T18:00:00Z|2026-11-10T19:00:00Z|America/Toronto|Local centre|Mosque|old|local:source"""
        assertThrows(IllegalArgumentException::class.java) { CommunitySnapshotImporter.parse(content, Instant.EPOCH) }
    }
}
