package app.timeline.islamic.core.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.timeline.islamic.core.security.AttachmentEncryptionKeyProvider
import app.timeline.islamic.core.security.EncryptedAttachmentStore
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDocumentAttachmentRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomDocumentAttachmentRepository
    private lateinit var store: EncryptedAttachmentStore
    private lateinit var root: File

    private val keyProvider = AttachmentEncryptionKeyProvider { _ ->
        SecretKeySpec(ByteArray(32) { (it * 11 + 5).toByte() }, "AES")
    }

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = Files.createTempDirectory("timeline-room-attachments").toFile()
        store = EncryptedAttachmentStore(root, keyProvider)
        repository = RoomDocumentAttachmentRepository(database, store)
    }

    @After fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test fun importStoresOnlyCiphertextAndReadAuthenticatesBeforeReturningContent(): Unit = runBlocking {
        val content = "private note / ملاحظة خاصة".toByteArray(Charsets.UTF_8)
        val row = repository.import(ByteArrayInputStream(content), "text/plain", "USER_MANAGED")
        assertEquals(content.size.toLong(), row.sizeBytes)
        assertEquals("v1/${row.id}.tla", row.encryptedPath)
        val encrypted = File(root, "${row.id}.tla").readBytes()
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("private note"))
        val actual = repository.withContent(row.id) { it.readBytes() }
        assertArrayEquals(content, actual)
    }

    @Test fun duplicateContentRollsBackNewCiphertextAfterRoomUniqueHashFailure(): Unit = runBlocking {
        val content = "same content".toByteArray()
        val first = repository.import(ByteArrayInputStream(content), "text/plain", "USER_MANAGED")
        val failed = runCatching {
            repository.import(ByteArrayInputStream(content), "text/plain", "USER_MANAGED")
        }
        assertTrue(failed.isFailure)
        assertEquals(listOf(first.id), repository.list().map { it.id })
        assertEquals(1, root.listFiles().orEmpty().count { it.extension == "tla" })
    }

    @Test fun deleteRemovesMetadataAndEncryptedFile(): Unit = runBlocking {
        val row = repository.import(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "application/octet-stream", "USER_MANAGED")
        assertTrue(store.exists(row.id))
        val result = repository.delete(row.id)
        assertTrue(result is AttachmentDeleteResult.Deleted)
        assertTrue((result as AttachmentDeleteResult.Deleted).encryptedFileRemoved)
        assertEquals(null, repository.get(row.id))
        assertFalse(store.exists(row.id))
    }

    @Test fun startupReconciliationRemovesOrphansAndReportsMissingRows(): Unit = runBlocking {
        val row = repository.import(ByteArrayInputStream(byteArrayOf(6, 7, 8)), "application/octet-stream", "USER_MANAGED")
        store.encrypt("orphan-id", ByteArrayInputStream(byteArrayOf(9, 10)))
        assertTrue(store.deleteEncrypted(row.id))

        val report = repository.reconcile()
        assertEquals(listOf(row.id), report.missingDatabaseReferencedFiles)
        assertEquals(1, report.orphanedEncryptedFilesDeleted)
        assertFalse(store.exists("orphan-id"))
    }
}
