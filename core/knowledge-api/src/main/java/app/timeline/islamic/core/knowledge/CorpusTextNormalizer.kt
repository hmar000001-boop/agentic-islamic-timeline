package app.timeline.islamic.core.knowledge

import java.text.Normalizer
import java.util.Locale

/** Search-only normalization. Never mutate or replace the original source transcription with this. */
object CorpusTextNormalizer {
    const val VERSION = "timeline-normalizer-v1"
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+")

    fun normalize(input: String): NormalizedQuery {
        val compatibility = Normalizer.normalize(input, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val output = StringBuilder(compatibility.length)
        compatibility.codePoints().forEach { cp ->
            if (cp == 0x0640) return@forEach // Arabic tatweel
            val type = Character.getType(cp)
            if (type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()) return@forEach
            val mapped = when (cp) {
                0x0622, 0x0623, 0x0625, 0x0671 -> 0x0627 // alef variants
                0x0649 -> 0x064A // alef maqsura -> ya
                0x0624 -> 0x0648 // waw with hamza -> waw
                0x0626 -> 0x064A // ya with hamza -> ya
                else -> cp
            }
            output.appendCodePoint(mapped)
        }
        val normalized = output.toString().replace(Regex("\\s+"), " ").trim()
        val tokens = tokenPattern.findAll(normalized).map { it.value }.distinct().take(64).toList()
        // Quoted terms prevent user-supplied FTS operators, column names and syntax from being executed.
        val expression = tokens.joinToString(" OR ") { "\"$it\"" }
        return NormalizedQuery(normalized, tokens, expression)
    }
}

/** Stable citation identity independent of pack version and safe against delimiter ambiguity. */
object EvidenceIdentity {
    private const val DOMAIN = "timeline-evidence-key-v1\n"

    fun stableKey(sourceId: String, editionId: String, locator: String, language: CorpusLanguage): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN.toByteArray(Charsets.UTF_8))
        listOf(sourceId, editionId, locator, language.code).forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(byteArrayOf(
                (bytes.size ushr 24).toByte(),
                (bytes.size ushr 16).toByte(),
                (bytes.size ushr 8).toByte(),
                bytes.size.toByte()
            ))
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun contentSha256(originalText: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(originalText.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
