package app.timeline.islamic.core.persistence

import app.timeline.islamic.core.agent.CanvasDomain
import app.timeline.islamic.core.agent.CanvasRepository
import app.timeline.islamic.core.agent.CanvasSaveResult
import app.timeline.islamic.core.agent.CanvasStatus
import app.timeline.islamic.core.agent.CanvasWorkspace
import app.timeline.islamic.core.common.Clock
import app.timeline.islamic.core.common.SystemClock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The entire Room file is SQLCipher-encrypted. These bytes are therefore protected at rest
 * by the database cipher; they are not a substitute for a separate attachment encryption layer.
 */
class RoomCanvasRepository(private val dao: CanvasDao, private val clock: Clock = SystemClock) : CanvasRepository {
    override fun observeWorkspaces(): Flow<List<CanvasWorkspace>> =
        dao.observeSessions().map { rows -> rows.map(::decode) }

    override suspend fun getWorkspace(id: String): CanvasWorkspace? = dao.getSession(id)?.let(::decode)

    override suspend fun create(title: String, domain: CanvasDomain, notes: String): CanvasWorkspace {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Canvas title is required" }
        require(normalizedTitle.length <= MAX_TITLE_LENGTH)
        require(notes.toByteArray(Charsets.UTF_8).size <= MAX_NOTES_BYTES)
        val now = clock.now()
        val id = UUID.randomUUID().toString()
        val state = CanvasDraftCodec.encode(normalizedTitle, notes)
        val session = CanvasSessionEntity(
            id = id, domain = domain.name, status = CanvasStatus.DRAFT.name,
            encryptedState = state, currentStep = "OVERVIEW", version = 1,
            stateSchemaVersion = CanvasDraftCodec.VERSION, updatedAtEpochMillis = now.toEpochMilli()
        )
        dao.createWithSnapshot(session, snapshot(id, 1, state, now))
        return CanvasWorkspace(id, domain, normalizedTitle, notes, CanvasStatus.DRAFT, "OVERVIEW", 1, now)
    }

    override suspend fun updateDraft(
        id: String, expectedVersion: Long, title: String, notes: String
    ): CanvasSaveResult {
        val current = dao.getSession(id) ?: return CanvasSaveResult.NotFound
        if (current.version != expectedVersion) return CanvasSaveResult.Conflict
        if (current.status == CanvasStatus.ARCHIVED.name) return CanvasSaveResult.Conflict
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Canvas title is required" }
        require(normalizedTitle.length <= MAX_TITLE_LENGTH)
        require(notes.toByteArray(Charsets.UTF_8).size <= MAX_NOTES_BYTES)
        val now = clock.now()
        val state = CanvasDraftCodec.encode(normalizedTitle, notes)
        val updated = current.copy(encryptedState = state, version = current.version + 1, updatedAtEpochMillis = now.toEpochMilli())
        val saved = dao.updateWithSnapshot(updated, expectedVersion, snapshot(id, updated.version, state, now))
        if (!saved) return CanvasSaveResult.Conflict
        return CanvasSaveResult.Saved(
            CanvasWorkspace(id, CanvasDomain.valueOf(current.domain), normalizedTitle, notes,
                CanvasStatus.valueOf(current.status), current.currentStep, updated.version, now)
        )
    }

    override suspend fun archive(id: String, expectedVersion: Long): CanvasSaveResult {
        val current = dao.getSession(id) ?: return CanvasSaveResult.NotFound
        if (current.version != expectedVersion) return CanvasSaveResult.Conflict
        if (current.status == CanvasStatus.ARCHIVED.name) return CanvasSaveResult.Saved(decode(current))
        val now = clock.now()
        val updated = current.copy(status = CanvasStatus.ARCHIVED.name, version = current.version + 1, updatedAtEpochMillis = now.toEpochMilli())
        val saved = dao.updateWithSnapshot(updated, expectedVersion, snapshot(id, updated.version, updated.encryptedState, now))
        if (!saved) return CanvasSaveResult.Conflict
        return CanvasSaveResult.Saved(decode(updated))
    }

    private fun snapshot(id: String, version: Long, payload: ByteArray, now: Instant) = CanvasSnapshotEntity(
        id = UUID.randomUUID().toString(), canvasId = id, snapshotVersion = version,
        encryptedPayload = payload, payloadHash = sha256(payload), createdAtEpochMillis = now.toEpochMilli()
    )

    private fun decode(entity: CanvasSessionEntity): CanvasWorkspace {
        require(entity.stateSchemaVersion == CanvasDraftCodec.VERSION) { "Unsupported Canvas state schema" }
        val decoded = CanvasDraftCodec.decode(entity.encryptedState)
        return CanvasWorkspace(
            entity.id, CanvasDomain.valueOf(entity.domain), decoded.first, decoded.second,
            CanvasStatus.valueOf(entity.status), entity.currentStep, entity.version,
            Instant.ofEpochMilli(entity.updatedAtEpochMillis)
        )
    }

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_TITLE_LENGTH = 120
        const val MAX_NOTES_BYTES = 400_000
    }
}

/** Length-prefixed UTF-8 draft format; the version is stored alongside every Room row. */
internal object CanvasDraftCodec {
    const val VERSION = 1
    fun encode(title: String, notes: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(VERSION)
            out.writeUTF(title)
            val noteBytes = notes.toByteArray(Charsets.UTF_8)
            out.writeInt(noteBytes.size)
            out.write(noteBytes)
        }
        bytes.toByteArray()
    }

    fun decode(payload: ByteArray): Pair<String, String> = DataInputStream(ByteArrayInputStream(payload)).use { input ->
        val version = input.readInt()
        require(version == VERSION) { "Unsupported Canvas draft schema: $version" }
        val title = input.readUTF()
        val noteLength = input.readInt()
        require(noteLength in 0..400_000) { "Invalid Canvas notes length" }
        val noteBytes = ByteArray(noteLength)
        input.readFully(noteBytes)
        require(input.available() == 0) { "Trailing bytes in Canvas draft" }
        title to noteBytes.toString(Charsets.UTF_8)
    }
}
