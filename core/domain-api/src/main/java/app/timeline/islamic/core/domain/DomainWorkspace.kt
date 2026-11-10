package app.timeline.islamic.core.domain

import java.security.MessageDigest

/** Domain payloads remain inside the SQLCipher Room database. No domain repository may write them to plaintext files. */
class DomainWorkspaceRecord(
    val id: String,
    val domain: String,
    val schemaVersion: Int,
    val revision: Long,
    val status: String,
    payload: ByteArray,
    val payloadSha256: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    private val bytes = payload.copyOf()
    val payload: ByteArray get() = bytes.copyOf()

    init {
        require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")))
        require(domain.matches(Regex("[a-z][a-z0-9-]{1,47}")))
        require(schemaVersion in 1..32)
        require(revision > 0)
        require(status.matches(Regex("[A-Z][A-Z0-9_]{0,47}")))
        require(bytes.isNotEmpty() && bytes.size <= MAX_PAYLOAD_BYTES)
        require(payloadSha256.matches(Regex("[a-f0-9]{64}")))
        require(createdAtEpochMillis > 0 && updatedAtEpochMillis >= createdAtEpochMillis)
        require(MessageDigest.isEqual(sha256(bytes), payloadSha256.hexBytes())) { "Domain workspace payload hash mismatch" }
    }

    companion object {
        const val MAX_PAYLOAD_BYTES = 512 * 1024
        fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
        private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

interface DomainWorkspaceRepository {
    suspend fun create(
        id: String,
        domain: String,
        schemaVersion: Int,
        status: String,
        payload: ByteArray,
        nowEpochMillis: Long
    ): DomainWorkspaceRecord

    suspend fun read(id: String): DomainWorkspaceRecord?
    suspend fun list(domain: String, limit: Int = 100): List<DomainWorkspaceRecord>
    /** Compare-and-set update. False means another writer changed the record. */
    suspend fun update(
        id: String,
        expectedRevision: Long,
        status: String,
        payload: ByteArray,
        nowEpochMillis: Long
    ): Boolean
    suspend fun delete(id: String, expectedRevision: Long): Boolean
}

/** Deterministic, bounded key/value codec for small versioned domain drafts. */
object DomainPayloadCodec {
    private const val MAGIC = 0x544C4450 // TLDP
    private const val CODEC_VERSION = 1
    private const val MAX_FIELDS = 128
    private const val MAX_FIELD_BYTES = 64 * 1024

    fun encode(fields: Map<String, String>): ByteArray {
        require(fields.size in 1..MAX_FIELDS)
        val normalized = fields.toSortedMap()
        val output = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(CODEC_VERSION)
            data.writeInt(normalized.size)
            normalized.forEach { (key, value) ->
                require(key.matches(Regex("[a-z][a-zA-Z0-9._-]{0,63}")))
                writeString(data, key)
                writeString(data, value)
            }
        }
        return output.toByteArray().also { require(it.isNotEmpty() && it.size <= DomainWorkspaceRecord.MAX_PAYLOAD_BYTES) }
    }

    fun decode(bytes: ByteArray): Map<String, String> {
        require(bytes.isNotEmpty() && bytes.size <= DomainWorkspaceRecord.MAX_PAYLOAD_BYTES)
        java.io.DataInputStream(java.io.ByteArrayInputStream(bytes)).use { data ->
            require(data.readInt() == MAGIC) { "Invalid domain payload magic" }
            require(data.readInt() == CODEC_VERSION) { "Unsupported domain payload codec" }
            val size = data.readInt()
            require(size in 1..MAX_FIELDS)
            val fields = linkedMapOf<String, String>()
            repeat(size) {
                val key = readString(data)
                require(key.matches(Regex("[a-z][a-zA-Z0-9._-]{0,63}"))) { "Invalid domain payload field key" }
                val value = readString(data)
                require(fields.put(key, value) == null) { "Duplicate domain payload field" }
            }
            require(data.available() == 0) { "Trailing domain payload bytes" }
            return fields
        }
    }

    private fun writeString(data: java.io.DataOutputStream, value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_FIELD_BYTES)
        data.writeInt(encoded.size)
        data.write(encoded)
    }

    private fun readString(data: java.io.DataInputStream): String {
        val size = data.readInt()
        require(size in 0..MAX_FIELD_BYTES && size <= data.available())
        val bytes = ByteArray(size)
        data.readFully(bytes)
        return Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }
}
