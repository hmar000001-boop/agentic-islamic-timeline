package app.timeline.islamic.core.security

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedAttachmentStoreTest {
    private val key = SecretKeySpec(ByteArray(32) { (it * 7 + 3).toByte() }, "AES")

    @Test fun encryptsAndAuthenticatesContentBeforeCallingConsumer(): Unit = withTempDirectory { directory ->
        val store = EncryptedAttachmentStore(directory, AttachmentEncryptionKeyProvider { _ -> key })
        val plaintext = "Private notes: مراجعة سورة البقرة".toByteArray(Charsets.UTF_8)
        val metadata = store.encrypt("attachment-1", ByteArrayInputStream(plaintext))
        val encryptedFile = File(directory, "attachment-1.tla")

        assertEquals("v1/attachment-1.tla", metadata.relativePath)
        assertEquals(plaintext.size.toLong(), metadata.sizeBytes)
        assertFalse(encryptedFile.readBytes().toString(Charsets.UTF_8).contains("Private notes"))

        var callbackInvoked = false
        val restored = store.withDecryptedAttachment("attachment-1", metadata.sizeBytes, metadata.sha256) {
            callbackInvoked = true
            it.readBytes()
        }
        assertTrue(callbackInvoked)
        assertArrayEquals(plaintext, restored)
        assertFalse(File(directory, ".decrypt-temp").listFiles().orEmpty().any { it.name.endsWith(".plain.tmp") })
    }

    @Test fun samePlaintextUsesDistinctProviderGeneratedGcmIvs(): Unit = withTempDirectory { directory ->
        val store = EncryptedAttachmentStore(directory, AttachmentEncryptionKeyProvider { _ -> key })
        val plaintext = "same payload".toByteArray()
        store.encrypt("iv-one", ByteArrayInputStream(plaintext))
        store.encrypt("iv-two", ByteArrayInputStream(plaintext))
        val firstHeader = File(directory, "iv-one.tla").readBytes().copyOfRange(5, 17)
        val secondHeader = File(directory, "iv-two.tla").readBytes().copyOfRange(5, 17)
        assertEquals(12, firstHeader.size)
        assertEquals(12, secondHeader.size)
        assertFalse(firstHeader.contentEquals(secondHeader))
    }

    @Test fun rejectsCiphertextTamperingWithoutExposingPlaintext(): Unit = withTempDirectory { directory ->
        val store = EncryptedAttachmentStore(directory, AttachmentEncryptionKeyProvider { _ -> key })
        val plaintext = "private attachment content".toByteArray()
        val metadata = store.encrypt("tamper-me", ByteArrayInputStream(plaintext))
        val file = File(directory, "tamper-me.tla")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        file.writeBytes(bytes)

        var callbackInvoked = false
        val failure = runCatching {
            store.withDecryptedAttachment("tamper-me", metadata.sizeBytes, metadata.sha256) {
                callbackInvoked = true
                it.readBytes()
            }
        }.exceptionOrNull()
        assertTrue(failure is EncryptedAttachmentException)
        assertFalse(callbackInvoked)
        assertFalse(File(directory, ".decrypt-temp").listFiles().orEmpty().any { it.name.endsWith(".plain.tmp") })
    }

    @Test fun ciphertextCannotBeMovedToAnotherAttachmentId(): Unit = withTempDirectory { directory ->
        val store = EncryptedAttachmentStore(directory, AttachmentEncryptionKeyProvider { _ -> key })
        val plaintext = "id-bound payload".toByteArray()
        val metadata = store.encrypt("original-id", ByteArrayInputStream(plaintext))
        Files.copy(File(directory, "original-id.tla").toPath(), File(directory, "other-id.tla").toPath())

        val failure = runCatching {
            store.withDecryptedAttachment("other-id", metadata.sizeBytes, metadata.sha256) { it.readBytes() }
        }.exceptionOrNull()
        assertTrue(failure is EncryptedAttachmentException)
    }

    @Test fun enforcesStreamingSizeBoundAndDoesNotInstallPartialFile(): Unit = withTempDirectory { directory ->
        val store = EncryptedAttachmentStore(directory, AttachmentEncryptionKeyProvider { _ -> key }, maximumPlaintextBytes = 8)
        val failure = runCatching {
            store.encrypt("too-large", ByteArrayInputStream(ByteArray(9) { 4 }))
        }.exceptionOrNull()
        assertTrue(failure is AttachmentTooLargeException)
        assertFalse(File(directory, "too-large.tla").exists())
        assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".partial") })
    }

    @Test fun rejectsOversizedCiphertextBeforeDecryption(): Unit = withTempDirectory { directory ->
        val store = EncryptedAttachmentStore(directory, AttachmentEncryptionKeyProvider { _ -> key }, maximumPlaintextBytes = 64)
        val metadata = store.encrypt("bounded-ciphertext", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        val encrypted = File(directory, "bounded-ciphertext.tla")
        java.io.RandomAccessFile(encrypted, "rw").use { it.setLength(17L + 16L + 65L) }
        val failure = runCatching {
            store.withDecryptedAttachment("bounded-ciphertext", metadata.sizeBytes, metadata.sha256) { it.readBytes() }
        }.exceptionOrNull()
        assertTrue(failure is EncryptedAttachmentException)
    }

    @Test fun rejectsPathTraversalAndDoesNotOverwriteExistingId(): Unit = withTempDirectory { directory ->
        val store = EncryptedAttachmentStore(directory, AttachmentEncryptionKeyProvider { _ -> key })
        assertTrue(runCatching { store.encrypt("../escape", ByteArrayInputStream(byteArrayOf(1))) }.isFailure)
        store.encrypt("stable-id", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        assertTrue(runCatching { store.encrypt("stable-id", ByteArrayInputStream(byteArrayOf(4))) }.isFailure)
    }

    @Test fun doesNotCreateReplacementKeyWhenEncryptedAttachmentsAlreadyExist(): Unit = withTempDirectory { directory ->
        var keyAvailable: javax.crypto.SecretKey? = key
        val provider = AttachmentEncryptionKeyProvider { allowCreate ->
            keyAvailable ?: if (allowCreate) key.also { keyAvailable = it } else error("Key unavailable; existing ciphertext must not be orphaned")
        }
        val store = EncryptedAttachmentStore(directory, provider)
        store.encrypt("keep-key", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        keyAvailable = null // Simulate a missing/invalidated Android Keystore key.
        val failure = runCatching {
            store.encrypt("second-file", ByteArrayInputStream(byteArrayOf(4, 5, 6)))
        }.exceptionOrNull()
        assertTrue(failure is EncryptedAttachmentException)
        val decryptFailure = runCatching {
            store.withDecryptedAttachment("keep-key", 3, "00".repeat(32)) { it.readBytes() }
        }.exceptionOrNull()
        assertTrue(decryptFailure is EncryptedAttachmentException)
        assertTrue(File(directory, "keep-key.tla").exists())
        assertFalse(File(directory, "second-file.tla").exists())
    }

    @Test fun verifiesStoredSizeAndDigestIndependentlyOfGcmAuthentication(): Unit = withTempDirectory { directory ->
        val store = EncryptedAttachmentStore(directory, AttachmentEncryptionKeyProvider { _ -> key })
        val plaintext = "verified metadata".toByteArray()
        val metadata = store.encrypt("metadata-check", ByteArrayInputStream(plaintext))
        val incorrectHash = "00".repeat(32)
        assertTrue(
            runCatching {
                store.withDecryptedAttachment("metadata-check", metadata.sizeBytes, incorrectHash) { it.readBytes() }
            }.exceptionOrNull() is EncryptedAttachmentException
        )
        val actual = MessageDigest.getInstance("SHA-256").digest(plaintext).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(metadata.sha256, actual)
    }

    private inline fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("timeline-attachment-test").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
