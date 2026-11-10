package app.timeline.islamic

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.Locale

/** Validates the deliberately narrow attachment types before bytes reach encrypted storage. */
internal object AttachmentImportPolicy {
    const val PDF_MIME_TYPE = "application/pdf"
    const val TEXT_MIME_TYPE = "text/plain"
    const val RETENTION_POLICY_USER_MANAGED = "USER_MANAGED"

    private val allowedMimeTypes = setOf(PDF_MIME_TYPE, TEXT_MIME_TYPE)
    private val pdfSignature = "%PDF-".toByteArray(Charsets.US_ASCII)

    data class ValidatedStream(
        val stream: BufferedInputStream,
        val mimeType: String
    )

    /**
     * Does not consume the stream: a bounded prefix is inspected and then rewound. The caller
     * still owns and must close the original ContentResolver stream.
     */
    fun validate(content: InputStream, reportedMimeType: String?): ValidatedStream {
        val mimeType = reportedMimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: throw IllegalArgumentException("Attachment provider did not report a MIME type")
        require(mimeType in allowedMimeTypes) { "Unsupported attachment MIME type" }

        val buffered = if (content is BufferedInputStream) content else BufferedInputStream(content, 8 * 1024)
        buffered.mark(pdfSignature.size + 1)
        val prefix = ByteArray(pdfSignature.size)
        var offset = 0
        while (offset < prefix.size) {
            val count = buffered.read(prefix, offset, prefix.size - offset)
            if (count < 0) break
            if (count == 0) continue
            offset += count
        }
        buffered.reset()

        val isPdf = offset == pdfSignature.size && prefix.contentEquals(pdfSignature)
        require((mimeType == PDF_MIME_TYPE) == isPdf) {
            "Attachment MIME type does not match its file signature"
        }
        return ValidatedStream(buffered, mimeType)
    }
}
