package app.timeline.islamic.core.corpus

import app.timeline.islamic.core.knowledge.CorpusLanguage
import app.timeline.islamic.core.knowledge.CorpusPackManifest
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Strict, signed, canonical UTF-8 manifest parser for Corpus Pack format v1. */
object CorpusPackManifestCodec {
    const val FORMAT_LINE = "format=timeline-corpus-pack"
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private val idPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    private val versionPattern = Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?")
    private val sha256Pattern = Regex("[a-f0-9]{64}")

    private val fieldOrder = listOf(
        "format", "schema_version", "pack_id", "pack_version", "created_at_epoch_ms",
        "key_id", "license_profile", "source_count", "passage_count", "index_file",
        "index_sha256", "languages"
    )

    fun parse(bytes: ByteArray): CorpusPackManifest {
        require(bytes.isNotEmpty() && bytes.size <= MAX_MANIFEST_BYTES) { "Manifest size is invalid" }
        require(!(bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())) {
            "UTF-8 BOM is forbidden in signed manifest"
        }
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException("Manifest is not valid UTF-8", e)
        }
        require('\r' !in text) { "Manifest must use LF line endings" }
        require(text.endsWith("\n")) { "Manifest must end with exactly one LF" }
        require(!text.endsWith("\n\n")) { "Blank trailing manifest lines are forbidden" }
        val lines = text.dropLast(1).split('\n')
        require(lines.size == fieldOrder.size) { "Manifest field count does not match schema v1" }
        val values = LinkedHashMap<String, String>()
        lines.forEachIndexed { index, line ->
            val separator = line.indexOf('=')
            require(separator > 0 && separator == line.lastIndexOf('=')) { "Malformed manifest line ${index + 1}" }
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            require(key == fieldOrder[index]) { "Manifest field ${index + 1} must be '${fieldOrder[index]}'" }
            require(value.isNotEmpty()) { "Manifest field '$key' is empty" }
            require(values.put(key, value) == null) { "Duplicate manifest field '$key'" }
        }
        require(values.getValue("format") == "timeline-corpus-pack") { "Unsupported corpus pack format" }
        val schemaVersion = values.getValue("schema_version").toIntStrict("schema_version")
        require(schemaVersion == 1) { "Unsupported corpus pack schema version $schemaVersion" }
        val packId = values.getValue("pack_id")
        require(idPattern.matches(packId)) { "Invalid pack_id" }
        val version = values.getValue("pack_version")
        require(versionPattern.matches(version)) { "pack_version must be semantic version x.y.z[-label]" }
        val createdAt = values.getValue("created_at_epoch_ms").toLongStrict("created_at_epoch_ms")
        require(createdAt > 0) { "created_at_epoch_ms must be positive" }
        val keyId = values.getValue("key_id")
        require(idPattern.matches(keyId)) { "Invalid key_id" }
        val licenseProfile = values.getValue("license_profile")
        require(idPattern.matches(licenseProfile)) { "Invalid license_profile" }
        val sourceCount = values.getValue("source_count").toIntStrict("source_count")
        val passageCount = values.getValue("passage_count").toIntStrict("passage_count")
        require(sourceCount in 1..100_000) { "source_count is outside the supported range" }
        require(passageCount in 1..20_000_000) { "passage_count is outside the supported range" }
        require(values.getValue("index_file") == "corpus.sqlite") { "Only corpus.sqlite is supported in schema v1" }
        val indexSha = values.getValue("index_sha256")
        require(sha256Pattern.matches(indexSha)) { "index_sha256 must be lowercase SHA-256 hex" }
        val languageCodes = values.getValue("languages").split(',')
        require(languageCodes.isNotEmpty() && languageCodes.size == languageCodes.distinct().size) { "languages must be unique" }
        val languages = languageCodes.map { code -> CorpusLanguage.fromCode(code) ?: error("Unsupported language '$code'") }.toSet()
        require(languages.map { it.code }.sorted() == languageCodes) { "languages must be sorted by ISO code" }
        val manifest = CorpusPackManifest(
            schemaVersion, packId, version, createdAt, keyId, licenseProfile,
            sourceCount, passageCount, "corpus.sqlite", indexSha, languages
        )
        require(encode(manifest).contentEquals(bytes)) { "Manifest is not in canonical form" }
        return manifest
    }

    fun encode(manifest: CorpusPackManifest): ByteArray {
        val codes = manifest.languages.map { it.code }.sorted().joinToString(",")
        val value = listOf(
            FORMAT_LINE,
            "schema_version=${manifest.schemaVersion}",
            "pack_id=${manifest.packId}",
            "pack_version=${manifest.packVersion}",
            "created_at_epoch_ms=${manifest.createdAtEpochMillis}",
            "key_id=${manifest.keyId}",
            "license_profile=${manifest.licenseProfile}",
            "source_count=${manifest.sourceCount}",
            "passage_count=${manifest.passageCount}",
            "index_file=${manifest.indexFile}",
            "index_sha256=${manifest.indexSha256}",
            "languages=$codes"
        ).joinToString("\n", postfix = "\n")
        return value.toByteArray(StandardCharsets.UTF_8)
    }

    private fun String.toIntStrict(field: String): Int = toIntOrNull() ?: throw IllegalArgumentException("Invalid integer in $field")
    private fun String.toLongStrict(field: String): Long = toLongOrNull() ?: throw IllegalArgumentException("Invalid integer in $field")
}
