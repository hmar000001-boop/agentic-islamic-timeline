package app.timeline.islamic

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AttachmentImportPolicyTest {
    @Test
    fun acceptsPdfWhenSignatureMatchesAndLeavesStreamAtBeginning() {
        val source = "%PDF-1.7\nbody".toByteArray(Charsets.US_ASCII)
        val validated = AttachmentImportPolicy.validate(
            ByteArrayInputStream(source),
            "application/pdf"
        )

        assertEquals(AttachmentImportPolicy.PDF_MIME_TYPE, validated.mimeType)
        assertArrayEquals(source, validated.stream.readBytes())
    }

    @Test
    fun acceptsUtf8TextAndLeavesStreamAtBeginning() {
        val source = "بسم الله الرحمن الرحيم\nA note".toByteArray(Charsets.UTF_8)
        val validated = AttachmentImportPolicy.validate(
            ByteArrayInputStream(source),
            "TEXT/PLAIN; charset=utf-8"
        )

        assertEquals(AttachmentImportPolicy.TEXT_MIME_TYPE, validated.mimeType)
        assertArrayEquals(source, validated.stream.readBytes())
    }

    @Test
    fun rejectsPdfLabeledAsPlainText() {
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentImportPolicy.validate(
                ByteArrayInputStream("%PDF-1.7".toByteArray(Charsets.US_ASCII)),
                "text/plain"
            )
        }
    }

    @Test
    fun rejectsNonPdfContentLabeledAsPdf() {
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentImportPolicy.validate(
                ByteArrayInputStream("not a pdf".toByteArray(Charsets.US_ASCII)),
                "application/pdf"
            )
        }
    }

    @Test
    fun rejectsMissingAndUnsupportedMimeTypes() {
        val content = ByteArrayInputStream("plain text".toByteArray(Charsets.UTF_8))
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentImportPolicy.validate(content, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentImportPolicy.validate(
                ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                "application/octet-stream"
            )
        }
    }
}
