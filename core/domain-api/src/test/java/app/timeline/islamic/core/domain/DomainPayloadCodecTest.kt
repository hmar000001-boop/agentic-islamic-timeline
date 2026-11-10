package app.timeline.islamic.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainPayloadCodecTest {
    @Test fun encodingIsDeterministicAndRoundTripsUnicode() {
        val first = DomainPayloadCodec.encode(linkedMapOf("z" to "last", "arabic" to "مراجعة", "a" to "first"))
        val second = DomainPayloadCodec.encode(linkedMapOf("a" to "first", "z" to "last", "arabic" to "مراجعة"))
        assertEquals(first.toList(), second.toList())
        assertEquals(mapOf("a" to "first", "arabic" to "مراجعة", "z" to "last"), DomainPayloadCodec.decode(first))
    }

    @Test fun rejectsTrailingBytesAndOversizedPayload() {
        val encoded = DomainPayloadCodec.encode(mapOf("status" to "DRAFT"))
        assertThrows(IllegalArgumentException::class.java) { DomainPayloadCodec.decode(encoded + byteArrayOf(0)) }
        assertThrows(IllegalArgumentException::class.java) { DomainPayloadCodec.encode(mapOf("value" to "x".repeat(65 * 1024))) }
    }

    @Test fun recordDefensivelyCopiesPayloadAndChecksSha256() {
        val input = byteArrayOf(1, 2, 3)
        val hash = DomainWorkspaceRecord.hex(DomainWorkspaceRecord.sha256(input))
        val record = DomainWorkspaceRecord("draft-1", "prayer", 1, 1, "DRAFT", input, hash, 100, 100)
        input[0] = 9
        assertEquals(1, record.payload[0].toInt())
        assertThrows(IllegalArgumentException::class.java) {
            DomainWorkspaceRecord("draft-2", "prayer", 1, 1, "DRAFT", byteArrayOf(9), hash, 100, 100)
        }
    }
}
