package app.timeline.islamic.core.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CanvasDraftCodecTest {
    @Test fun roundTripsArabicAndEnglishDrafts() {
        val notes = """Revise Surah Al-Baqarah
مراجعة سورة البقرة
Keep exact source references."""
        val bytes = CanvasDraftCodec.encode("Hifz plan / خطة الحفظ", notes)
        val decoded = CanvasDraftCodec.decode(bytes)
        assertEquals("Hifz plan / خطة الحفظ", decoded.first)
        assertEquals(notes, decoded.second)
    }

    @Test fun rejectsUnsupportedSchema() {
        val bytes = CanvasDraftCodec.encode("draft", "notes")
        bytes[3] = 2
        assertThrows(IllegalArgumentException::class.java) { CanvasDraftCodec.decode(bytes) }
    }

    @Test fun rejectsTrailingBytes() {
        val bytes = CanvasDraftCodec.encode("draft", "notes") + byteArrayOf(0x01)
        assertThrows(IllegalArgumentException::class.java) { CanvasDraftCodec.decode(bytes) }
    }
}
