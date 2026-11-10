package app.timeline.islamic.core.corpus

import app.timeline.islamic.core.knowledge.PublisherKeyringEnvelope
import app.timeline.islamic.core.knowledge.PublisherKeyringStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SignedPublisherKeyringArchiveImporterTest {
    @Test fun importsExactRootSignedTwoEntryArchive(): Unit = runBlocking {
        val root = PinnedPublisherRoot("root", ByteArray(32) { 7 })
        val verifier = Verifier()
        val store = MemoryStore()
        val manager = PublisherKeyringManager(root, store, verifier) { 1_000L }
        val payload = PublisherKeyringCodec.encode(PublisherKeyringDocument(
            1, 900, listOf(PublisherKeyRecord("publisher", ByteArray(32) { 3 }, 100, null))
        ))
        val signature = verifier.signature(payload, root.rawPublicKey)
        val archive = zip(listOf("keyring.txt" to payload, "keyring.sig" to signature))
        assertEquals(1L, SignedPublisherKeyringArchiveImporter(manager).import(ByteArrayInputStream(archive)))
        assertNotNull(manager.find("publisher"))
    }

    @Test fun rejectsUnexpectedArchivePaths(): Unit = runBlocking {
        val importer = SignedPublisherKeyringArchiveImporter(manager())
        val entries = listOf("keyring.txt" to byteArrayOf(1), "keyring.sig" to ByteArray(64), "../escape" to byteArrayOf(1))
        try { importer.import(ByteArrayInputStream(zip(entries))); fail("Unexpected archive paths must fail") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsArchiveAndExpandedEntryOverLimits(): Unit = runBlocking {
        val manager = manager()
        val importer = SignedPublisherKeyringArchiveImporter(manager)
        val oversized = "x".repeat(300 * 1024).toByteArray()
        try {
            importer.import(ByteArrayInputStream(zip(listOf("keyring.txt" to oversized, "keyring.sig" to ByteArray(64)))))
            fail("Oversized payload must fail")
        } catch (_: IllegalArgumentException) { }
    }

    private fun manager(): PublisherKeyringManager = PublisherKeyringManager(
        PinnedPublisherRoot("root", ByteArray(32) { 7 }), MemoryStore(), Verifier()
    ) { 1_000L }

    private fun zip(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
        output.toByteArray()
    }

    private class Verifier : PackSignatureVerifier {
        fun signature(message: ByteArray, key: ByteArray): ByteArray {
            val hash = MessageDigest.getInstance("SHA-256").digest(message + key)
            return hash + hash
        }
        override fun verify(message: ByteArray, signature: ByteArray, rawPublicKey: ByteArray) =
            signature.contentEquals(signatureFor(message, rawPublicKey))
        private fun signatureFor(message: ByteArray, key: ByteArray): ByteArray {
            val hash = MessageDigest.getInstance("SHA-256").digest(message + key)
            return hash + hash
        }
    }

    private class MemoryStore : PublisherKeyringStore {
        private var value: PublisherKeyringEnvelope? = null
        override suspend fun read() = value
        override suspend fun writeIfCurrentVersion(expectedCurrentVersion: Long, envelope: PublisherKeyringEnvelope): Boolean {
            if ((value?.version ?: 0L) != expectedCurrentVersion || envelope.version <= expectedCurrentVersion) return false
            value = envelope; return true
        }
    }
}
