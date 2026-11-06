package app.timeline.islamic.core.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.timeline.islamic.core.security.DatabaseKeyManager
import app.timeline.islamic.core.security.DatabaseKeyUnavailableException
import app.timeline.islamic.core.timeline.TimelineRepository
import java.time.Instant
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "phase1-encrypted-test.db"
    private val databaseFile: File get() = context.getDatabasePath(databaseName)

    @After fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test fun encryptedRoomDatabaseCanCloseAndReopen() = runBlocking {
        System.loadLibrary("sqlcipher")
        val passphrase = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
        fun open(): AppDatabase = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase.copyOf()))
            .build()

        val first = open()
        first.timelineDao().upsert(
            TimelineItemEntity("test-1", "EVENT", "Local persistence test", 1_799_000_000_000L,
                null, "UTC", "USER", "CONFIRMED", null)
        )
        first.close()

        // SQLCipher database files must not expose SQLite's plaintext file header.
        val prefix = databaseFile.readBytes().take(16).toByteArray()
        assertFalse(prefix.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)))

        val second = open()
        val repository: TimelineRepository = RoomTimelineRepository(second.timelineDao())
        val items = repository.observeItems(Instant.EPOCH, Instant.ofEpochMilli(Long.MAX_VALUE)).first()
        assertEquals("test-1", items.single().id)
        assertEquals("Local persistence test", items.single().title)
        second.close()
    }

    @Test fun keystoreEnvelopeRoundTripsDatabasePassphrase() {
        val isolatedName = "phase1-key-roundtrip-${System.nanoTime()}.db"
        val envelopeName = "key-roundtrip-${System.nanoTime()}.envelope"
        val isolatedAlias = "app.timeline.test.wrap.${System.nanoTime()}"
        try {
            val first = DatabaseKeyManager(context, isolatedName, envelopeName, isolatedAlias).getOrCreatePassphrase()
            val second = DatabaseKeyManager(context, isolatedName, envelopeName, isolatedAlias).getOrCreatePassphrase()
            assertEquals(first.toList(), second.toList())
        } finally {
            File(context.noBackupFilesDir, envelopeName).delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(isolatedAlias) }
        }
    }

    @Test fun existingDatabaseWithoutKeyEnvelopeFailsClosed() {
        val isolatedName = "phase1-missing-key-${System.nanoTime()}.db"
        val existingDb = context.getDatabasePath(isolatedName)
        existingDb.parentFile?.mkdirs()
        existingDb.writeBytes(byteArrayOf(0x01, 0x02, 0x03))
        val manager = DatabaseKeyManager(
            context = context,
            databaseName = isolatedName,
            envelopeName = "missing-envelope-${System.nanoTime()}",
            keyAlias = "app.timeline.test.missing.${System.nanoTime()}"
        )
        try {
            manager.getOrCreatePassphrase()
            throw AssertionError("Expected database-key recovery to fail closed")
        } catch (_: DatabaseKeyUnavailableException) {
            // Expected: never generate a new key for an existing database.
        } finally {
            existingDb.delete()
        }
    }
}
