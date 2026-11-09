package app.timeline.islamic.core.corpus

import app.timeline.islamic.core.knowledge.PublisherKeyringEnvelope
import app.timeline.islamic.core.knowledge.PublisherKeyringStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class PublisherKeyringManagerTest {
    @Test fun onlyRootVerifiedMonotonicKeyringsArePublished(): Unit = runBlocking {
        val store = MemoryStore()
        val root = PinnedPublisherRoot("offline-root-v1", ByteArray(32) { 9 })
        val verifier = TestVerifier()
        val manager = PublisherKeyringManager(root, store, verifier) { 1_000L }
        val v1 = document(1, 100)
        val payload1 = PublisherKeyringCodec.encode(v1)
        val sig1 = verifier.expectedSignature(payload1, root.rawPublicKey)
        assertEquals(1, manager.install(payload1, sig1))
        assertEquals(1, manager.version())
        assertNotNull(manager.find("publisher-1"))
        assertNull(manager.find("not-present"))

        val payload2 = PublisherKeyringCodec.encode(document(2, 200))
        assertEquals(2, manager.install(payload2, verifier.expectedSignature(payload2, root.rawPublicKey)))
        assertEquals(2, manager.version())
        try {
            manager.install(payload1, sig1)
            fail("Keyring rollback must be rejected")
        } catch (_: PublisherKeyringException) { }
        assertEquals(2, manager.version())
    }

    @Test fun refreshReverifiesStoredBytesAndFutureOrBadSignaturesFailClosed(): Unit = runBlocking {
        val store = MemoryStore()
        val root = PinnedPublisherRoot("offline-root-v1", ByteArray(32) { 8 })
        val verifier = TestVerifier()
        val manager = PublisherKeyringManager(root, store, verifier) { 1_000L }
        val doc = document(4, 100)
        val payload = PublisherKeyringCodec.encode(doc)
        store.writeIfCurrentVersion(0L, PublisherKeyringEnvelope(4, payload, ByteArray(64) { 0 }, 900))
        try {
            manager.refresh()
            fail("Stored keyring with invalid root signature must be rejected")
        } catch (_: PublisherKeyringException) { }
        assertFalse(manager.isReady())

        val futureManager = PublisherKeyringManager(root, MemoryStore(), verifier) { 100L }
        val futurePayload = PublisherKeyringCodec.encode(document(5, 200))
        try {
            futureManager.install(futurePayload, verifier.expectedSignature(futurePayload, root.rawPublicKey))
            fail("Future keyring generation time must fail")
        } catch (_: PublisherKeyringException) { }
    }


    @Test fun keyIdsCannotBeReboundRemovedOrUnrevoked(): Unit = runBlocking {
        val store = MemoryStore()
        val root = PinnedPublisherRoot("offline-root-v1", ByteArray(32) { 6 })
        val verifier = TestVerifier()
        val manager = PublisherKeyringManager(root, store, verifier) { 2_000L }
        val original = document(1, 100)
        val first = PublisherKeyringCodec.encode(original)
        manager.install(first, verifier.expectedSignature(first, root.rawPublicKey))

        val rebound = PublisherKeyringCodec.encode(PublisherKeyringDocument(
            2, 200, listOf(PublisherKeyRecord("publisher-1", ByteArray(32) { 4 }, 50, null))
        ))
        try { manager.install(rebound, verifier.expectedSignature(rebound, root.rawPublicKey)); fail("Key ID rebinding must fail") }
        catch (_: PublisherKeyringException) { }
        assertEquals(1L, manager.version())

        val rotated = PublisherKeyringCodec.encode(PublisherKeyringDocument(
            2, 200, listOf(
                PublisherKeyRecord("publisher-1", ByteArray(32) { 3 }, 50, 1_500),
                PublisherKeyRecord("publisher-2", ByteArray(32) { 5 }, 1_000, null)
            )
        ))
        manager.install(rotated, verifier.expectedSignature(rotated, root.rawPublicKey))
        val unrevoked = PublisherKeyringCodec.encode(PublisherKeyringDocument(
            3, 300, listOf(
                PublisherKeyRecord("publisher-1", ByteArray(32) { 3 }, 50, null),
                PublisherKeyRecord("publisher-2", ByteArray(32) { 5 }, 1_000, null)
            )
        ))
        try { manager.install(unrevoked, verifier.expectedSignature(unrevoked, root.rawPublicKey)); fail("Revocation cannot be undone") }
        catch (_: PublisherKeyringException) { }
        assertEquals(2L, manager.version())
    }

    private fun document(version: Long, generated: Long) = PublisherKeyringDocument(
        version, generated,
        listOf(PublisherKeyRecord("publisher-1", ByteArray(32) { 3 }, 50, null))
    )

    private class TestVerifier : PackSignatureVerifier {
        fun expectedSignature(message: ByteArray, key: ByteArray): ByteArray {
            val hash = MessageDigest.getInstance("SHA-256").digest(message + key)
            return hash + hash
        }
        override fun verify(message: ByteArray, signature: ByteArray, rawPublicKey: ByteArray): Boolean =
            signature.contentEquals(expectedSignature(message, rawPublicKey))
    }

    private class MemoryStore : PublisherKeyringStore {
        private var current: PublisherKeyringEnvelope? = null
        override suspend fun read(): PublisherKeyringEnvelope? = current
        override suspend fun writeIfCurrentVersion(expectedCurrentVersion: Long, envelope: PublisherKeyringEnvelope): Boolean {
            if ((current?.version ?: 0L) != expectedCurrentVersion || envelope.version <= expectedCurrentVersion) return false
            current = envelope
            return true
        }
    }
}
