package app.timeline.islamic.core.persistence

import androidx.room.withTransaction
import app.timeline.islamic.core.knowledge.PublisherKeyringEnvelope
import app.timeline.islamic.core.knowledge.PublisherKeyringStore

/** Persists publisher trust state only in the existing SQLCipher-protected database. */
class RoomPublisherKeyringStore(private val database: AppDatabase) : PublisherKeyringStore {
    override suspend fun read(): PublisherKeyringEnvelope? = database.publisherKeyringDao().current()?.let {
        PublisherKeyringEnvelope(it.version, it.payload, it.signature, it.installedAtEpochMillis)
    }

    override suspend fun writeIfCurrentVersion(expectedCurrentVersion: Long, envelope: PublisherKeyringEnvelope): Boolean = database.withTransaction {
        val dao = database.publisherKeyringDao()
        val current = dao.current()
        if ((current?.version ?: 0L) != expectedCurrentVersion || envelope.version <= expectedCurrentVersion) return@withTransaction false
        dao.put(
            PublisherKeyringStateEntity(
                id = ROOT_STATE_ID,
                version = envelope.version,
                payload = envelope.payload,
                signature = envelope.signature,
                installedAtEpochMillis = envelope.installedAtEpochMillis
            )
        )
        true
    }

    private companion object { const val ROOT_STATE_ID = "publisher-root" }
}
