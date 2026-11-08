package app.timeline.islamic.core.corpus

import org.junit.Assert.*
import org.junit.Test

class PublisherKeyringCodecTest {
    @Test fun canonicalRoundTripPreservesRotationAndRevocationMetadata() {
        val document = PublisherKeyringDocument(
            version = 7,
            generatedAtEpochMillis = 1_000,
            publishers = listOf(
                PublisherKeyRecord("publisher-a", ByteArray(32) { 1 }, 100, null),
                PublisherKeyRecord("publisher-b", ByteArray(32) { 2 }, 200, 900)
            )
        )
        val encoded = PublisherKeyringCodec.encode(document)
        val parsed = PublisherKeyringCodec.parse(encoded)
        assertEquals(7, parsed.version)
        assertEquals(listOf("publisher-a", "publisher-b"), parsed.publishers.map { it.keyId })
        assertEquals(900L, parsed.publishers[1].revokedAtEpochMillis ?: -1L)
        assertTrue(PublisherKeyringCodec.encode(parsed).contentEquals(encoded))
    }

    @Test fun rejectsUnsortedKeysNonCanonicalLinesAndDuplicateIds() {
        try {
            PublisherKeyringDocument(1, 100, listOf(
                PublisherKeyRecord("z-key", ByteArray(32) { 1 }, 1, null),
                PublisherKeyRecord("a-key", ByteArray(32) { 2 }, 1, null)
            ))
            fail("Unsorted documents must not be constructed")
        } catch (_: IllegalArgumentException) { }
        try {
            PublisherKeyringDocument(1, 100, listOf(
                PublisherKeyRecord("a-key", ByteArray(32) { 1 }, 1, null),
                PublisherKeyRecord("a-key", ByteArray(32) { 2 }, 1, null)
            ))
            fail("Duplicate publisher IDs must be rejected")
        } catch (_: IllegalArgumentException) { }

        val canonical = PublisherKeyringCodec.encode(PublisherKeyringDocument(
            1, 100, listOf(PublisherKeyRecord("a-key", ByteArray(32) { 1 }, 1, null))
        ))
        val nonCanonical = canonical.copyOf(canonical.size + 1)
        try { PublisherKeyringCodec.parse(nonCanonical); fail("Extra blank line must fail") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun defensiveKeyBytesAreNotExposedByTrustedPublisherKey() {
        val input = ByteArray(32) { 4 }
        val key = TrustedPublisherKey("publisher-a", input, 10)
        input[0] = 99
        val exposed = key.rawPublicKey
        exposed[1] = 88
        assertEquals(4, key.rawPublicKey[0].toInt())
        assertEquals(4, key.rawPublicKey[1].toInt())
    }
}
