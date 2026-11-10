package app.timeline.islamic.core.persistence

import androidx.room.withTransaction
import app.timeline.islamic.core.agent.AuditLogPort
import app.timeline.islamic.core.agent.AuditMacProvider
import app.timeline.islamic.core.agent.AuditVerification
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Local HMAC-linked audit chain. Payload bytes are hashed, never stored or logged. */
class RoomAuditLog(private val db: AppDatabase, private val macProvider: AuditMacProvider) : AuditLogPort {
    override suspend fun append(eventType: String, payload: ByteArray, at: Instant): Long {
        requireEvent(eventType, payload)
        return db.withTransaction { appendWithinTransaction(eventType, payload, at) }
    }

    /** Must be called only from an active Room transaction when combining audit with a state mutation. */
    internal suspend fun appendWithinTransaction(eventType: String, payload: ByteArray, at: Instant): Long {
        requireEvent(eventType, payload)
        val previous = db.auditDao().allAscending()
        verifyRows(previous)?.let { throw IllegalStateException("Audit chain verification failed: $it") }
        val sequence = (previous.lastOrNull()?.sequence ?: 0L) + 1L
        val previousMac = previous.lastOrNull()?.recordMac
        val payloadHash = sha256(payload)
        val macInput = encode(sequence, eventType, at.toEpochMilli(), payloadHash, previousMac)
        val mac = macProvider.computeMac(macInput, allowCreate = previous.isEmpty()).toHex()
        db.auditDao().append(AuditEventEntity(
            id = UUID.randomUUID().toString(), sequence = sequence, eventType = eventType,
            timestampEpochMillis = at.toEpochMilli(), payloadHash = payloadHash,
            previousMac = previousMac, recordMac = mac
        ))
        return sequence
    }

    override suspend fun verify(): AuditVerification {
        val rows = db.auditDao().allAscending()
        val problem = verifyRows(rows)
        return if (problem == null) AuditVerification.Valid(rows.size.toLong()) else AuditVerification.Invalid(problem)
    }

    private fun verifyRows(rows: List<AuditEventEntity>): String? {
        if (rows.isEmpty()) return null
        var previousMac: String? = null
        var expectedSequence = 1L
        rows.forEach { row ->
            if (row.sequence != expectedSequence) return "sequence gap at ${row.sequence}"
            if (row.previousMac != previousMac) return "previous MAC mismatch at ${row.sequence}"
            if (!row.payloadHash.matches(Regex("[0-9a-f]{64}")) || !row.recordMac.matches(Regex("[0-9a-f]{64}"))) {
                return "malformed hash at ${row.sequence}"
            }
            val expected = try {
                macProvider.computeMac(
                    encode(row.sequence, row.eventType, row.timestampEpochMillis, row.payloadHash, row.previousMac),
                    allowCreate = false
                ).toHex()
            } catch (_: Exception) {
                return "Keystore audit key unavailable"
            }
            if (!constantTimeEquals(expected, row.recordMac)) return "record MAC mismatch at ${row.sequence}"
            previousMac = row.recordMac
            expectedSequence++
        }
        return null
    }

    private fun requireEvent(eventType: String, payload: ByteArray) {
        require(eventType.isNotBlank() && eventType.length <= 120)
        require(payload.size <= MAX_PAYLOAD_BYTES)
    }

    private fun encode(sequence: Long, type: String, timestamp: Long, payloadHash: String, previousMac: String?): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(AUDIT_FORMAT_VERSION)
                out.writeLong(sequence)
                out.writeUTF(type)
                out.writeLong(timestamp)
                out.writeUTF(payloadHash)
                out.writeBoolean(previousMac != null)
                if (previousMac != null) out.writeUTF(previousMac)
            }
            bytes.toByteArray()
        }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun constantTimeEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(left.toByteArray(Charsets.US_ASCII), right.toByteArray(Charsets.US_ASCII))

    private companion object {
        const val AUDIT_FORMAT_VERSION = 1
        const val MAX_PAYLOAD_BYTES = 64 * 1024
    }
}
