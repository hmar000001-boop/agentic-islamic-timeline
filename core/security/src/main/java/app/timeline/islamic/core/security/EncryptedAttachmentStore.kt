package app.timeline.islamic.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A non-exportable Android Keystore AES key used only for attachment payload encryption. */
fun interface AttachmentEncryptionKeyProvider {
    /** Only create a new key when [allowCreate] is true and no encrypted payload depends on it. */
    fun getOrCreateKey(allowCreate: Boolean): SecretKey
}

class KeystoreAttachmentEncryptionKeyProvider(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : AttachmentEncryptionKeyProvider {
    override fun getOrCreateKey(allowCreate: Boolean): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = store.getKey(keyAlias, null)
        if (existing != null) {
            return existing as? SecretKey
                ?: throw IllegalStateException("Attachment Keystore alias is occupied by a non-secret key")
        }
        // An alias that exists but can no longer be read is treated as invalidated. Do not silently
        // generate a replacement key: that would make existing attachment ciphertext unrecoverable.
        if (store.containsAlias(keyAlias) || !allowCreate) {
            throw IllegalStateException("Attachment Keystore key is unavailable or invalidated")
        }
        val generator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "app.timeline.islamic.attachment.aes.v1"
    }
}

class EncryptedAttachmentException(message: String, cause: Throwable? = null) : Exception(message, cause)
class AttachmentTooLargeException(message: String) : Exception(message)

data class EncryptedAttachmentMetadata(
    val id: String,
    /** A stable relative path; never an absolute path or a caller-controlled path. */
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String
)

data class AttachmentFileReconciliation(
    val orphanedFilesDeleted: Int,
    val temporaryFilesDeleted: Int,
    val retainedFileIds: Set<String>
)

/**
 * Streaming AES-256-GCM storage for private user attachments.
 *
 * Encrypted originals are stored below noBackupFilesDir/attachments/v1. The constructor taking
 * [rootDirectory] is intended for isolated tests; production callers should use the Context
 * constructor so Android's no-backup area is selected. AAD binds ciphertext to its attachment ID
 * and format version. Decryption is completed and authenticated into a short-lived private temp
 * file before [withDecryptedAttachment] exposes an input stream to the callback.
 */
class EncryptedAttachmentStore(
    private val rootDirectory: File,
    private val keyProvider: AttachmentEncryptionKeyProvider,
    private val maximumPlaintextBytes: Long = DEFAULT_MAXIMUM_PLAINTEXT_BYTES,
    private val plaintextTemporaryDirectory: File = File(rootDirectory, ".decrypt-temp")
) {
    constructor(
        context: Context,
        keyProvider: AttachmentEncryptionKeyProvider = KeystoreAttachmentEncryptionKeyProvider(),
        maximumPlaintextBytes: Long = DEFAULT_MAXIMUM_PLAINTEXT_BYTES
    ) : this(
        File(File(context.applicationContext.noBackupFilesDir, ATTACHMENT_DIRECTORY), FORMAT_DIRECTORY),
        keyProvider,
        maximumPlaintextBytes,
        plaintextTemporaryDirectory = File(context.applicationContext.cacheDir, DECRYPT_TEMP_DIRECTORY)
    )

    init {
        require(maximumPlaintextBytes in 1..HARD_MAXIMUM_PLAINTEXT_BYTES) {
            "Attachment maximum must be between 1 byte and $HARD_MAXIMUM_PLAINTEXT_BYTES bytes"
        }
    }

    /** Consumes but does not close [plaintext]. Existing IDs are never overwritten. */
    @Synchronized
    @Throws(EncryptedAttachmentException::class, AttachmentTooLargeException::class)
    fun encrypt(id: String, plaintext: InputStream): EncryptedAttachmentMetadata {
        validateId(id)
        val directory = ensurePrivateDirectory()
        val destination = encryptedFile(id)
        if (destination.exists()) throw EncryptedAttachmentException("Attachment already exists")
        val temporary = File(directory, ".$id-${UUID.randomUUID()}.partial")
        var committed = false
        try {
            val key = keyProvider.getOrCreateKey(allowCreate = !hasEncryptedPayloadFiles())
            // Android Keystore rejects caller-provided encryption IVs while randomized encryption
            // is required. Let the provider generate the IV, then persist that exact value.
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
            val iv = cipher.iv
            if (iv == null || iv.size != GCM_IV_BYTES) {
                throw EncryptedAttachmentException("Cipher provider did not generate a 96-bit GCM IV")
            }
            cipher.updateAAD(associatedData(id))
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            FileOutputStream(temporary).use { output ->
                output.write(MAGIC)
                output.write(FORMAT_VERSION)
                output.write(iv)
                val buffer = ByteArray(IO_BUFFER_BYTES)
                while (true) {
                    var count = plaintext.read(buffer)
                    if (count < 0) break
                    if (count == 0) {
                        val singleByte = plaintext.read()
                        if (singleByte < 0) break
                        buffer[0] = singleByte.toByte()
                        count = 1
                    }
                    if (count.toLong() > maximumPlaintextBytes - total) {
                        throw AttachmentTooLargeException("Attachment exceeds the configured size limit")
                    }
                    total += count
                    digest.update(buffer, 0, count)
                    val encrypted = cipher.update(buffer, 0, count)
                    if (encrypted != null && encrypted.isNotEmpty()) output.write(encrypted)
                }
                if (total == 0L) throw EncryptedAttachmentException("Empty attachments are not accepted")
                output.write(cipher.doFinal())
                output.fd.sync()
            }
            if (destination.exists() || !temporary.renameTo(destination)) {
                throw EncryptedAttachmentException("Unable to atomically install encrypted attachment")
            }
            committed = true
            return EncryptedAttachmentMetadata(
                id = id,
                relativePath = relativePathFor(id),
                sizeBytes = total,
                sha256 = digest.digest().toHex()
            )
        } catch (error: EncryptedAttachmentException) {
            throw error
        } catch (error: AttachmentTooLargeException) {
            throw error
        } catch (error: Exception) {
            throw EncryptedAttachmentException("Unable to encrypt attachment", error)
        } finally {
            if (!committed) temporary.delete()
        }
    }

    /**
     * Authenticates the complete encrypted file and verifies the Room-recorded length and hash
     * before invoking [consumer]. The callback must consume the stream synchronously and must not
     * retain it or copy plaintext to shared/external storage.
     */
    @Synchronized
    @Throws(EncryptedAttachmentException::class)
    fun <T> withDecryptedAttachment(
        id: String,
        expectedPlaintextBytes: Long,
        expectedSha256: String,
        consumer: (InputStream) -> T
    ): T {
        validateId(id)
        require(expectedPlaintextBytes in 1..maximumPlaintextBytes)
        require(SHA256_PATTERN.matches(expectedSha256))
        val encrypted = encryptedFile(id)
        if (!encrypted.isFile) throw EncryptedAttachmentException("Encrypted attachment is missing")
        val minimumEncryptedBytes = HEADER_BYTES + GCM_TAG_BYTES + 1L
        val maximumEncryptedBytes = maximumPlaintextBytes + HEADER_BYTES + GCM_TAG_BYTES
        if (encrypted.length() !in minimumEncryptedBytes..maximumEncryptedBytes) {
            throw EncryptedAttachmentException("Encrypted attachment length is outside the supported bounds")
        }
        val directory = ensureTemporaryDirectory()
        val plaintextTemp = File(directory, ".$id-${UUID.randomUUID()}.plain.tmp")
        try {
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                DataInputStream(FileInputStream(encrypted)).use { input ->
                    val magic = ByteArray(MAGIC.size)
                    input.readFully(magic)
                    if (!magic.contentEquals(MAGIC)) throw EncryptedAttachmentException("Unsupported attachment header")
                    val version = input.readUnsignedByte()
                    if (version != FORMAT_VERSION) throw EncryptedAttachmentException("Unsupported attachment format version")
                    val iv = ByteArray(GCM_IV_BYTES)
                    input.readFully(iv)
                    val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
                        init(
                            Cipher.DECRYPT_MODE,
                            keyProvider.getOrCreateKey(allowCreate = false),
                            GCMParameterSpec(GCM_TAG_BITS, iv)
                        )
                        updateAAD(associatedData(id))
                    }
                    FileOutputStream(plaintextTemp).use { output ->
                        val buffer = ByteArray(IO_BUFFER_BYTES)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            val decoded = cipher.update(buffer, 0, count)
                            if (decoded != null && decoded.isNotEmpty()) {
                                if (decoded.size.toLong() > maximumPlaintextBytes - total) {
                                    throw EncryptedAttachmentException("Decrypted attachment exceeds the configured size limit")
                                }
                                total += decoded.size
                                digest.update(decoded)
                                output.write(decoded)
                            }
                        }
                        // GCM verifies the authentication tag here. Nothing is exposed to the caller
                        // until this returns successfully and the independent metadata checks pass.
                        val finalBytes = cipher.doFinal()
                        if (finalBytes.isNotEmpty()) {
                            if (finalBytes.size.toLong() > maximumPlaintextBytes - total) {
                                throw EncryptedAttachmentException("Decrypted attachment exceeds the configured size limit")
                            }
                            total += finalBytes.size
                            digest.update(finalBytes)
                            output.write(finalBytes)
                        }
                        output.fd.sync()
                    }
                }
                val actualHash = digest.digest().toHex()
                if (total != expectedPlaintextBytes || !constantTimeHexEquals(actualHash, expectedSha256)) {
                    throw EncryptedAttachmentException("Attachment metadata integrity check failed")
                }
            } catch (error: EncryptedAttachmentException) {
                throw error
            } catch (error: Exception) {
                throw EncryptedAttachmentException("Attachment authentication or decryption failed", error)
            }
            return FileInputStream(plaintextTemp).use(consumer)
        } finally {
            plaintextTemp.delete()
        }
    }

    @Synchronized
    fun exists(id: String): Boolean {
        validateId(id)
        return encryptedFile(id).isFile
    }

    /** Deletes the encrypted original. Missing files are considered already deleted. */
    @Synchronized
    fun deleteEncrypted(id: String): Boolean {
        validateId(id)
        val file = encryptedFile(id)
        return !file.exists() || file.delete()
    }

    /**
     * Startup reconciliation: remove encrypted files without Room rows and abandoned partial or
     * plaintext temporary files. IDs with a Room row but no file are returned as missing by the
     * repository layer; they are never silently recreated.
     */
    @Synchronized
    fun reconcile(validAttachmentIds: Set<String>): AttachmentFileReconciliation {
        validAttachmentIds.forEach(::validateId)
        val directory = ensurePrivateDirectory()
        val encryptedEntries = directory.listFiles()
            ?: throw IllegalStateException("Cannot enumerate encrypted attachment storage")
        val plaintextDirectory = ensureTemporaryDirectory()
        val plaintextEntries = plaintextDirectory.listFiles()
            ?: throw IllegalStateException("Cannot enumerate attachment decryption cache")
        var orphaned = 0
        var temporary = 0
        encryptedEntries.forEach { file ->
            when {
                file.name.endsWith(".tla") -> {
                    val id = file.name.removeSuffix(".tla")
                    if (id !in validAttachmentIds) {
                        if (!file.delete() && file.exists()) {
                            throw IllegalStateException("Cannot remove orphaned encrypted attachment")
                        }
                        orphaned++
                    }
                }
                file.name.endsWith(".partial") -> {
                    if (!file.delete() && file.exists()) {
                        throw IllegalStateException("Cannot remove abandoned encrypted temporary file")
                    }
                    temporary++
                }
            }
        }
        plaintextEntries.forEach { file ->
            if (file.name.endsWith(".plain.tmp")) {
                if (!file.delete() && file.exists()) {
                    throw IllegalStateException("Cannot remove abandoned plaintext temporary file")
                }
                temporary++
            }
        }
        return AttachmentFileReconciliation(orphaned, temporary, validAttachmentIds.filterTo(linkedSetOf(), ::exists))
    }

    fun relativePathFor(id: String): String {
        validateId(id)
        return "$FORMAT_DIRECTORY/$id.tla"
    }

    private fun ensurePrivateDirectory(): File {
        check(rootDirectory.exists() || rootDirectory.mkdirs()) { "Cannot create attachment directory" }
        val absoluteRoot = rootDirectory.absoluteFile
        val canonicalRoot = rootDirectory.canonicalFile
        check(canonicalRoot.isDirectory && canonicalRoot == absoluteRoot) {
            "Attachment storage directory is not a canonical private directory"
        }
        return canonicalRoot
    }

    private fun encryptedFile(id: String): File {
        val directory = ensurePrivateDirectory()
        val file = File(directory, "$id.tla")
        check(file.canonicalFile.parentFile == directory) { "Attachment path escaped its private directory" }
        return file
    }

    private fun hasEncryptedPayloadFiles(): Boolean {
        val files = ensurePrivateDirectory().listFiles()
            ?: throw IllegalStateException("Cannot inspect attachment storage before creating an encryption key")
        return files.any { it.name.endsWith(".tla") }
    }

    private fun ensureTemporaryDirectory(): File {
        check(plaintextTemporaryDirectory.exists() || plaintextTemporaryDirectory.mkdirs()) {
            "Cannot create private attachment decryption cache"
        }
        val absoluteTemporaryDirectory = plaintextTemporaryDirectory.absoluteFile
        val canonicalTemporaryDirectory = plaintextTemporaryDirectory.canonicalFile
        check(canonicalTemporaryDirectory.isDirectory && canonicalTemporaryDirectory == absoluteTemporaryDirectory) {
            "Attachment decryption cache is not a canonical private directory"
        }
        return canonicalTemporaryDirectory
    }

    private fun associatedData(id: String): ByteArray = "timeline-attachment:$FORMAT_VERSION:$id".toByteArray(Charsets.UTF_8)

    private fun validateId(id: String) {
        require(ID_PATTERN.matches(id)) { "Invalid attachment identifier" }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun constantTimeHexEquals(left: String, right: String): Boolean {
        if (left.length != right.length) return false
        var difference = 0
        for (index in left.indices) difference = difference or (left[index].code xor right[index].code)
        return difference == 0
    }

    companion object {
        const val DEFAULT_MAXIMUM_PLAINTEXT_BYTES = 25L * 1024L * 1024L
        const val HARD_MAXIMUM_PLAINTEXT_BYTES = 100L * 1024L * 1024L
        private const val ATTACHMENT_DIRECTORY = "attachments"
        private const val DECRYPT_TEMP_DIRECTORY = "timeline-attachment-decrypt"
        private const val FORMAT_DIRECTORY = "v1"
        private const val FORMAT_VERSION = 1
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = 16L
        private const val HEADER_BYTES = 17L
        private const val IO_BUFFER_BYTES = 32 * 1024
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private val MAGIC = byteArrayOf(0x54, 0x4c, 0x41, 0x31) // TLA1
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
