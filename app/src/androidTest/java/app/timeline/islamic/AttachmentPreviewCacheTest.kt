package app.timeline.islamic

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentPreviewCacheTest {
    @Test
    fun startupCleanupDeletesOnlyOwnedPdfPreviewFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedCache = File(context.cacheDir, "part3-test-${UUID.randomUUID()}")
        val previewDirectory = File(isolatedCache, "attachment-previews")
        assertTrue(previewDirectory.mkdirs())
        val stalePreview = File(previewDirectory, "preview-stale.pdf")
        val unrelated = File(previewDirectory, "keep-me.txt")
        assertTrue(stalePreview.writeText("private plaintext fixture").let { stalePreview.isFile })
        assertTrue(unrelated.writeText("unrelated cache data").let { unrelated.isFile })

        try {
            assertTrue(AttachmentPreviewLoader.cleanupOrphanedPreviews(isolatedCache) == 1)
            assertFalse(stalePreview.exists())
            assertTrue(unrelated.isFile)
        } finally {
            isolatedCache.deleteRecursively()
        }
    }
    @Test
    fun textPreviewIsBoundedAndReportsTruncation() {
        val content = "a".repeat(AttachmentPreviewLoader.MAXIMUM_TEXT_PREVIEW_CHARACTERS + 17)
        val preview = AttachmentPreviewLoader.readText(content.byteInputStream(Charsets.UTF_8))

        assertTrue(preview.truncated)
        assertTrue(preview.content.length == AttachmentPreviewLoader.MAXIMUM_TEXT_PREVIEW_CHARACTERS)
    }

    @Test
    fun pdfPreviewRendersOnePageAndDeletesPlaintextTemporaryFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "attachment-previews")
        AttachmentPreviewLoader.cleanupOrphanedPreviews(context.cacheDir)

        val preview = AttachmentPreviewLoader.renderPdf(context, ByteArrayInputStream(minimalPdf()), 0)
        try {
            assertTrue(preview.pageIndex == 0)
            assertTrue(preview.pageCount == 1)
            assertTrue(preview.bitmap.width in 1..1440)
            assertTrue(preview.bitmap.height in 1..1440)
            assertTrue(
                directory.listFiles().orEmpty().none {
                    it.name.startsWith("preview-") && it.name.endsWith(".pdf")
                }
            )
        } finally {
            if (!preview.bitmap.isRecycled) preview.bitmap.recycle()
        }
    }

    private fun minimalPdf(): ByteArray {
        val output = ByteArrayOutputStream()
        val offsets = mutableListOf(0)
        fun append(value: String) {
            output.write(value.toByteArray(Charsets.US_ASCII))
        }

        append("%PDF-1.4\n")
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Resources << >> >>"
        )
        objects.forEachIndexed { index, body ->
            offsets += output.size()
            append("${index + 1} 0 obj\n$body\nendobj\n")
        }
        val xrefOffset = output.size()
        append("xref\n0 4\n0000000000 65535 f \n")
        offsets.drop(1).forEach { offset ->
            append(String.format(Locale.ROOT, "%010d 00000 n \n", offset))
        }
        append("trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return output.toByteArray()
    }

    @Test
    fun malformedUtf8TextIsRejectedRatherThanSilentlyReplaced() {
        val invalid = byteArrayOf(0xC3.toByte(), 0x28)
        val result = runCatching { AttachmentPreviewLoader.readText(invalid.inputStream()) }

        assertTrue(result.isFailure)
    }

}
