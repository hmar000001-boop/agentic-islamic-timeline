package app.timeline.islamic.core.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import app.timeline.islamic.core.knowledge.LocalEmbeddingModelDescriptor
import app.timeline.islamic.core.knowledge.QuantizedEmbeddingVector
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDenseEmbeddingIndexStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun encryptedRoomDenseIndexPublishesAndReplacesGenerationsAtomically(): Unit = runBlocking {
        System.loadLibrary("sqlcipher")
        val name = "phase4c-dense-index-${System.nanoTime()}.db"
        val passphrase = ByteArray(32).also(SecureRandom()::nextBytes)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
            .build()
        try {
            val store = RoomDenseEmbeddingIndexStore(database)
            val pack = InstalledCorpusPackRecord(
                packId = "fixture-pack",
                version = "1.0.0",
                manifestSha256 = "a".repeat(64),
                installedAtEpochMillis = 1_800_000_000_000L,
                licenseProfile = "fixture-cleared"
            )
            val model = LocalEmbeddingModelDescriptor("fixture-model", "1.0", "b".repeat(64), 16)
            val evidenceKey = "c".repeat(64)

            val first = store.beginGeneration(pack, model, 1_048_576L)
            store.appendVectors(first, mapOf(evidenceKey to QuantizedEmbeddingVector(16, ByteArray(16) { 7 })))
            // Staging rows are never visible as an active generation.
            assertNull(store.activeGeneration(pack.packId, pack.version, model.modelId, model.modelVersion))
            store.activateGeneration(first)
            assertEquals(first, store.activeGeneration(pack.packId, pack.version, model.modelId, model.modelVersion)?.generationId)
            assertEquals(16, store.vectorsFor(first, listOf(evidenceKey))[evidenceKey]?.dimension)

            val replacement = store.beginGeneration(pack, model, 1_048_576L)
            store.appendVectors(replacement, mapOf(evidenceKey to QuantizedEmbeddingVector(16, ByteArray(16) { 9 })))
            store.activateGeneration(replacement)

            assertEquals(replacement, store.activeGeneration(pack.packId, pack.version, model.modelId, model.modelVersion)?.generationId)
            assertTrue(store.vectorsFor(first, listOf(evidenceKey)).isEmpty())
            assertEquals(1, store.removePackVersion(pack.packId, pack.version))
            assertNull(store.activeGeneration(pack.packId, pack.version, model.modelId, model.modelVersion))
        } finally {
            database.close()
            context.deleteDatabase(name)
            File(context.getDatabasePath(name).path + "-wal").delete()
            File(context.getDatabasePath(name).path + "-shm").delete()
        }
    }

    @Test fun abandonedStagingGenerationIsRecoveredWithoutRemovingPreviousActiveGeneration(): Unit = runBlocking {
        System.loadLibrary("sqlcipher")
        val name = "phase4c-dense-recovery-${System.nanoTime()}.db"
        val passphrase = ByteArray(32).also(SecureRandom()::nextBytes)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
            .build()
        try {
            val store = RoomDenseEmbeddingIndexStore(database)
            val pack = InstalledCorpusPackRecord("fixture-pack", "1.0.0", "d".repeat(64), 1_800_000_000_000L, "fixture-cleared")
            val model = LocalEmbeddingModelDescriptor("fixture-model", "1.0", "e".repeat(64), 16)
            val key = "f".repeat(64)
            val active = store.beginGeneration(pack, model, 1_048_576L)
            store.appendVectors(active, mapOf(key to QuantizedEmbeddingVector(16, ByteArray(16) { 3 })))
            store.activateGeneration(active)
            val abandoned = store.beginGeneration(pack, model, 1_048_576L)
            store.appendVectors(abandoned, mapOf("1".repeat(64) to QuantizedEmbeddingVector(16, ByteArray(16) { 4 })))

            assertEquals(1, store.recoverAbandonedGenerations())
            assertEquals(active, store.activeGeneration(pack.packId, pack.version, model.modelId, model.modelVersion)?.generationId)
            assertFalse(store.vectorsFor(abandoned, listOf("1".repeat(64))).containsKey("1".repeat(64)))
        } finally {
            database.close()
            context.deleteDatabase(name)
            File(context.getDatabasePath(name).path + "-wal").delete()
            File(context.getDatabasePath(name).path + "-shm").delete()
        }
    }
}
