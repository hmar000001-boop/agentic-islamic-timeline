package app.timeline.islamic.core.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.timeline.islamic.core.knowledge.PublisherKeyringEnvelope
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublisherKeyringPersistenceTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() { database.close() }

    @Test fun publisherKeyringUpdatesUseCompareAndSetAndRetainExactSignedBytes() = runBlocking {
        val store = RoomPublisherKeyringStore(database)
        val first = PublisherKeyringEnvelope(1, "canonical-v1".toByteArray(), ByteArray(64) { 1 }, 1_000)
        assertTrue(store.writeIfCurrentVersion(0, first))
        assertFalse(store.writeIfCurrentVersion(0, PublisherKeyringEnvelope(2, byteArrayOf(2), ByteArray(64), 1_001)))
        val loaded = store.read()!!
        assertEquals(1L, loaded.version)
        assertArrayEquals("canonical-v1".toByteArray(), loaded.payload)
        assertArrayEquals(ByteArray(64) { 1 }, loaded.signature)
        val second = PublisherKeyringEnvelope(2, "canonical-v2".toByteArray(), ByteArray(64) { 2 }, 1_100)
        assertTrue(store.writeIfCurrentVersion(1, second))
        assertEquals(2L, store.read()!!.version)
    }

    @Test fun registryUpgradeQuarantineAndRemovalAreGuardedStateTransitions() = runBlocking {
        val registry = RoomCorpusPackRegistry(database)
        val first = InstalledCorpusPackRecord("test-pack", "1.0.0", "a".repeat(64), 1_000, "cleared")
        assertTrue(registry.registerAndCheck(first))
        val second = InstalledCorpusPackRecord("test-pack", "1.1.0", "b".repeat(64), 1_100, "cleared")
        assertTrue(registry.activateReplacement("1.0.0", second))
        assertFalse(registry.activateReplacement("1.0.0", InstalledCorpusPackRecord("test-pack", "1.2.0", "c".repeat(64), 1_200, "cleared")))
        assertEquals(1, registry.listInstalled().size)
        assertEquals(InstalledCorpusPackRecord.STATUS_SUPERSEDED, registry.listAll().single { it.version == "1.0.0" }.status)
        assertTrue(registry.transitionStatus(second.stableId, InstalledCorpusPackRecord.STATUS_INSTALLED, InstalledCorpusPackRecord.STATUS_QUARANTINED))
        assertTrue(registry.listInstalled().isEmpty())
        assertTrue(registry.transitionStatus(second.stableId, InstalledCorpusPackRecord.STATUS_QUARANTINED, InstalledCorpusPackRecord.STATUS_INSTALLED))
        assertFalse(registry.removeRecord(second.stableId))
        assertFalse(runCatching {
            registry.transitionStatus(first.stableId, InstalledCorpusPackRecord.STATUS_SUPERSEDED, InstalledCorpusPackRecord.STATUS_INSTALLED)
        }.isSuccess)
        assertTrue(registry.transitionStatus(second.stableId, InstalledCorpusPackRecord.STATUS_INSTALLED, InstalledCorpusPackRecord.STATUS_REMOVAL_PENDING))
        assertTrue(registry.removeRecord(second.stableId))
        assertEquals(0, registry.listInstalled().size)
    }

    private suspend fun RoomCorpusPackRegistry.registerAndCheck(record: app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord): Boolean {
        register(record)
        return isInstalled(record.packId, record.version)
    }
}
