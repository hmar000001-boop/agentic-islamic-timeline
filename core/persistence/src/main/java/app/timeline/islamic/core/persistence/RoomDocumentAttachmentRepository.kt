package app.timeline.islamic.core.persistence

import app.timeline.islamic.core.common.Clock
import app.timeline.islamic.core.common.SystemClock
import app.timeline.islamic.core.security.EncryptedAttachmentStore
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class AttachmentDeleteResult {
    object NotFound : AttachmentDeleteResult()
    data class Deleted(val encryptedFileRemoved: Boolean) : AttachmentDeleteResult()
}

data class AttachmentRecoveryReport(
    val orphanedEncryptedFilesDeleted: Int,
    val abandonedTemporaryFilesDeleted: Int,
    val missingDatabaseReferencedFiles: List<String>
)

/**
 * Coordinates encrypted attachment bytes with SQLCipher Room metadata.
 *
 * Import writes and fsyncs ciphertext before inserting metadata. If the Room insert fails, the
 * ciphertext is removed; if process death occurs between those operations, [reconcile] removes
 * the resulting orphan. Delete removes the SQLCipher row first and then ciphertext, so an
 * interrupted physical deletion leaves only an encrypted orphan that [reconcile] can safely purge.
 */
class RoomDocumentAttachmentRepository(
    private val database: AppDatabase,
    private val encryptedStore: EncryptedAttachmentStore,
    private val clock: Clock = SystemClock
) {
    private val mutex = Mutex()
    private val dao = database.userDomainDataDao()

    suspend fun import(
        content: InputStream,
        mimeType: String,
        retentionPolicy: String
    ): DocumentAttachmentEntity = mutex.withLock {
        require(MIME_PATTERN.matches(mimeType)) { "Invalid MIME type" }
        require(RETENTION_PATTERN.matches(retentionPolicy)) { "Invalid retention policy identifier" }
        val id = UUID.randomUUID().toString()
        // The bounded but potentially large stream and AES work must never run on the UI thread.
        // Finish the file write once started so cancellation cannot hide a committed file path.
        val encrypted = withContext(NonCancellable + Dispatchers.IO) {
            encryptedStore.encrypt(id, content)
        }
        val row = DocumentAttachmentEntity(
            id = id,
            encryptedPath = encrypted.relativePath,
            mimeType = mimeType,
            sizeBytes = encrypted.sizeBytes,
            sha256 = encrypted.sha256,
            retentionPolicy = retentionPolicy,
            importedAtEpochMillis = clock.now().toEpochMilli()
        )
        try {
            // Once ciphertext is committed, finish the Room insert even if the caller cancels;
            // otherwise cancellation could leave the row/file pair in an indeterminate state.
            withContext(NonCancellable) { dao.putAttachment(row) }
            row
        } catch (failure: Throwable) {
            try {
                val removed = withContext(NonCancellable + Dispatchers.IO) { encryptedStore.deleteEncrypted(id) }
                if (!removed) {
                    failure.addSuppressed(IllegalStateException("Failed to remove orphaned encrypted attachment $id"))
                }
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }

    suspend fun get(id: String): DocumentAttachmentEntity? = mutex.withLock {
        dao.attachment(id)
    }

    suspend fun list(): List<DocumentAttachmentEntity> = mutex.withLock {
        dao.attachments()
    }

    /**
     * The callback runs only after AES-GCM authentication and SQLCipher metadata size/hash checks
     * succeed. It must synchronously consume the stream and must not retain or export plaintext.
     */
    suspend fun <T> withContent(id: String, consumer: (InputStream) -> T): T? = mutex.withLock {
        val row = dao.attachment(id) ?: return@withLock null
        require(row.encryptedPath == encryptedStore.relativePathFor(row.id)) {
            "Attachment metadata path does not match its stable ID"
        }
        withContext(Dispatchers.IO) {
            encryptedStore.withDecryptedAttachment(row.id, row.sizeBytes, row.sha256, consumer)
        }
    }

    suspend fun delete(id: String): AttachmentDeleteResult = mutex.withLock {
        val row = dao.attachment(id) ?: return@withLock AttachmentDeleteResult.NotFound
        require(row.encryptedPath == encryptedStore.relativePathFor(row.id)) {
            "Attachment metadata path does not match its stable ID"
        }
        if (dao.deleteAttachment(id) != 1) return@withLock AttachmentDeleteResult.NotFound
        // A failed file deletion leaves only ciphertext. It is reported and recovered later;
        // user-visible metadata is not left pointing at a file that may already be gone.
        val removed = runCatching {
            withContext(NonCancellable + Dispatchers.IO) { encryptedStore.deleteEncrypted(id) }
        }.getOrDefault(false)
        AttachmentDeleteResult.Deleted(removed)
    }

    /** Run at startup after Room is available; missing referenced files are reported, never reset. */
    suspend fun reconcile(): AttachmentRecoveryReport = mutex.withLock {
        val rows = dao.attachments()
        rows.forEach { row ->
            require(row.encryptedPath == encryptedStore.relativePathFor(row.id)) {
                "Attachment metadata path does not match its stable ID"
            }
        }
        val existingIds = rows.mapTo(linkedSetOf()) { it.id }
        val (fileReport, missing) = withContext(Dispatchers.IO) {
            val report = encryptedStore.reconcile(existingIds)
            report to existingIds.filterNot(encryptedStore::exists).sorted()
        }
        AttachmentRecoveryReport(
            orphanedEncryptedFilesDeleted = fileReport.orphanedFilesDeleted,
            abandonedTemporaryFilesDeleted = fileReport.temporaryFilesDeleted,
            missingDatabaseReferencedFiles = missing
        )
    }

    private companion object {
        val MIME_PATTERN = Regex("[a-z0-9][a-z0-9!#$&^_.+-]{0,63}/[a-z0-9][a-z0-9!#$&^_.+-]{0,63}")
        val RETENTION_PATTERN = Regex("[A-Z][A-Z0-9_]{0,63}")
    }
}
