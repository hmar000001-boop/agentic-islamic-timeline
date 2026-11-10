package app.timeline.islamic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.math.sqrt

internal data class PdfAttachmentPreview(
    val bitmap: Bitmap,
    val pageIndex: Int,
    val pageCount: Int
)

/** Only creates short-lived plaintext in an app-private cache directory after GCM verification. */
internal object AttachmentPreviewLoader {
    const val MAXIMUM_TEXT_PREVIEW_CHARACTERS = 64 * 1024
    private const val PREVIEW_DIRECTORY = "attachment-previews"
    private const val PREVIEW_FILE_PREFIX = "preview-"
    private const val PREVIEW_FILE_SUFFIX = ".pdf"
    private const val MAX_PDF_BYTES = 25L * 1024L * 1024L
    private const val MAX_SUPPORTED_PDF_PAGES = 1_000
    private const val MAX_RENDER_DIMENSION = 1440
    private const val MAX_RENDER_PIXELS = 4_000_000.0

    data class TextPreview(val content: String, val truncated: Boolean)

    fun readText(input: InputStream): TextPreview {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        BufferedReader(InputStreamReader(input, decoder)).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4 * 1024)
            while (result.length < MAXIMUM_TEXT_PREVIEW_CHARACTERS) {
                val requested = minOf(buffer.size, MAXIMUM_TEXT_PREVIEW_CHARACTERS - result.length)
                val count = reader.read(buffer, 0, requested)
                if (count < 0) break
                if (count == 0) continue
                result.append(buffer, 0, count)
            }
            val truncated = reader.read() >= 0
            return TextPreview(result.toString(), truncated)
        }
    }

    @Synchronized
    fun renderPdf(context: Context, input: InputStream, requestedPageIndex: Int): PdfAttachmentPreview {
        val previewDirectory = ensurePrivatePreviewDirectory(context.cacheDir)
        val temporary = File.createTempFile(PREVIEW_FILE_PREFIX, PREVIEW_FILE_SUFFIX, previewDirectory)
        var bitmap: Bitmap? = null
        try {
            FileOutputStream(temporary).use { output ->
                copyBounded(input, output)
                output.fd.sync()
            }
            require(temporary.canonicalFile.parentFile == previewDirectory.canonicalFile) {
                "Preview file escaped its private cache directory"
            }
            ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    check(renderer.pageCount in 1..MAX_SUPPORTED_PDF_PAGES) {
                        "PDF page count is outside the supported bounds"
                    }
                    val pageIndex = requestedPageIndex.coerceIn(0, renderer.pageCount - 1)
                    val page = renderer.openPage(pageIndex)
                    try {
                        val width = page.width
                        val height = page.height
                        require(width > 0 && height > 0) { "PDF page dimensions are invalid" }
                        val dimensionScale = minOf(
                            1.0,
                            MAX_RENDER_DIMENSION.toDouble() / maxOf(width, height).toDouble()
                        )
                        val pixelScale = sqrt(MAX_RENDER_PIXELS / (width.toDouble() * height.toDouble()))
                        val scale = minOf(dimensionScale, pixelScale)
                        val renderWidth = maxOf(1, (width * scale).toInt())
                        val renderHeight = maxOf(1, (height * scale).toInt())
                        val renderedBitmap = Bitmap.createBitmap(
                            renderWidth,
                            renderHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        renderedBitmap.eraseColor(Color.WHITE)
                        page.render(
                            renderedBitmap,
                            Rect(0, 0, renderWidth, renderHeight),
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        bitmap = renderedBitmap
                        return PdfAttachmentPreview(renderedBitmap, pageIndex, renderer.pageCount)
                    } finally {
                        page.close()
                    }
                }
            }
        } finally {
            // The cache is private and excluded from backup. A failed deletion is surfaced; the
            // same narrowly scoped directory is retried at the next application startup.
            if (temporary.exists() && !temporary.delete()) {
                bitmap?.recycle()
                throw IOException("Unable to remove temporary plaintext PDF preview")
            }
        }
    }

    /** Removes only this feature's crash-leftover preview files; no other cache entries are read. */
    fun cleanupOrphanedPreviews(cacheDirectory: File): Int {
        val directory = File(cacheDirectory.canonicalFile, PREVIEW_DIRECTORY)
        if (Files.isSymbolicLink(directory.toPath())) {
            throw IOException("Attachment preview directory is a symbolic link")
        }
        if (!directory.exists()) return 0
        require(directory.isDirectory && directory.canonicalFile.parentFile == cacheDirectory.canonicalFile) {
            "Attachment preview directory is outside the private cache boundary"
        }
        var deleted = 0
        directory.listFiles().orEmpty().forEach { file ->
            if (file.name.startsWith(PREVIEW_FILE_PREFIX) && file.name.endsWith(PREVIEW_FILE_SUFFIX)) {
                if (!file.delete() && file.exists()) {
                    throw IOException("Unable to remove orphaned plaintext PDF preview")
                }
                deleted++
            }
        }
        return deleted
    }

    private fun ensurePrivatePreviewDirectory(cacheDirectory: File): File {
        val canonicalCache = cacheDirectory.canonicalFile
        require(canonicalCache.isDirectory) { "Application cache directory is unavailable" }
        val directory = File(canonicalCache, PREVIEW_DIRECTORY)
        if (Files.isSymbolicLink(directory.toPath())) {
            throw IOException("Attachment preview directory is a symbolic link")
        }
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Unable to create attachment preview directory")
        }
        require(directory.isDirectory && directory.canonicalFile.parentFile == canonicalCache) {
            "Attachment preview directory is outside the private cache boundary"
        }
        if (Files.isSymbolicLink(directory.toPath())) {
            throw IOException("Attachment preview directory changed during creation")
        }
        return directory
    }

    private fun copyBounded(input: InputStream, output: FileOutputStream) {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(count.toLong() <= MAX_PDF_BYTES - total) { "PDF preview exceeds the supported size" }
            total += count
            output.write(buffer, 0, count)
        }
        require(total > 0L) { "PDF preview is empty" }
    }
}
