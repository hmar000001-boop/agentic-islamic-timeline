package app.timeline.islamic.core.corpus

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** One publisher key including activation and revocation times, encoded as raw Ed25519 public-key bytes. */
data class PublisherKeyRecord(
    val keyId: String,
    val rawPublicKey: ByteArray,
    val notBeforeEpochMillis: Long,
    val revokedAtEpochMillis: Long?
) {
    init {
        require(keyId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")))
        require(rawPublicKey.size == 32)
        require(notBeforeEpochMillis > 0)
        require(revokedAtEpochMillis == null || revokedAtEpochMillis >= notBeforeEpochMillis)
    }

    override fun equals(other: Any?): Boolean = other is PublisherKeyRecord && keyId == other.keyId &&
        rawPublicKey.contentEquals(other.rawPublicKey) && notBeforeEpochMillis == other.notBeforeEpochMillis &&
        revokedAtEpochMillis == other.revokedAtEpochMillis
    override fun hashCode(): Int = 31 * keyId.hashCode() + rawPublicKey.contentHashCode()
}

data class PublisherKeyringDocument(
    val version: Long,
    val generatedAtEpochMillis: Long,
    val publishers: List<PublisherKeyRecord>
) {
    init {
        require(version > 0)
        require(generatedAtEpochMillis > 0)
        require(publishers.isNotEmpty() && publishers.size <= 256)
        require(publishers.map { it.keyId }.distinct().size == publishers.size)
        require(publishers == publishers.sortedBy { it.keyId }) { "Publisher keys must be sorted by key id" }
    }
}

/** Canonical text grammar. The root signature covers every byte of this file, including the final LF. */
object PublisherKeyringCodec {
    private const val MAX_BYTES = 256 * 1024
    private val idPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    private val hexPattern = Regex("[a-f0-9]{64}")

    fun parse(bytes: ByteArray): PublisherKeyringDocument {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Publisher keyring size is invalid" }
        require(!(bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()))
        val text = try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) { throw IllegalArgumentException("Publisher keyring is not valid UTF-8", e) }
        require('\r' !in text && text.endsWith("\n") && !text.endsWith("\n\n")) { "Publisher keyring must use canonical LF lines" }
        val lines = text.dropLast(1).split('\n')
        require(lines.size >= 4)
        require(lines[0] == "format=timeline-publisher-keyring")
        val version = field(lines[1], "version").toLongStrict()
        val generated = field(lines[2], "generated_at_epoch_ms").toLongStrict()
        val count = field(lines[3], "key_count").toIntStrict()
        require(count in 1..256 && lines.size == 4 + count) { "Publisher key count does not match entries" }
        val publishers = lines.drop(4).map { line ->
            val parts = line.split('|')
            require(parts.size == 4) { "Malformed publisher key entry" }
            val id = parts[0]
            require(idPattern.matches(id)) { "Invalid publisher key id" }
            require(hexPattern.matches(parts[1])) { "Publisher public key must be 32-byte lowercase hex" }
            val notBefore = parts[2].toLongStrict()
            val revoked = if (parts[3] == "-") null else parts[3].toLongStrict()
            PublisherKeyRecord(id, parts[1].hexBytes(), notBefore, revoked)
        }
        require(publishers.map { it.keyId } == publishers.map { it.keyId }.sorted()) { "Publisher entries must be sorted" }
        val document = PublisherKeyringDocument(version, generated, publishers)
        require(encode(document).contentEquals(bytes)) { "Publisher keyring is not canonical" }
        return document
    }

    fun encode(document: PublisherKeyringDocument): ByteArray = buildString {
        append("format=timeline-publisher-keyring\n")
        append("version=${document.version}\n")
        append("generated_at_epoch_ms=${document.generatedAtEpochMillis}\n")
        append("key_count=${document.publishers.size}\n")
        document.publishers.forEach { key ->
            append(key.keyId).append('|').append(key.rawPublicKey.toHex()).append('|')
                .append(key.notBeforeEpochMillis).append('|')
                .append(key.revokedAtEpochMillis?.toString() ?: "-").append('\n')
        }
    }.toByteArray(StandardCharsets.UTF_8)

    private fun field(line: String, name: String): String {
        val prefix = "$name="
        require(line.startsWith(prefix) && line.indexOf('=', prefix.length) < 0) { "Expected $name field" }
        return line.substring(prefix.length).also { require(it.isNotEmpty()) }
    }
    private fun String.toLongStrict(): Long = toLongOrNull()?.also { require(it > 0) } ?: error("Invalid positive integer")
    private fun String.toIntStrict(): Int = toIntOrNull() ?: error("Invalid integer")
    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
