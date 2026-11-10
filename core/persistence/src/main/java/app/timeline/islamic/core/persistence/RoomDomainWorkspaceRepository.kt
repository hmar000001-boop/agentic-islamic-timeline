package app.timeline.islamic.core.persistence

import app.timeline.islamic.core.domain.DomainWorkspaceRecord
import app.timeline.islamic.core.domain.DomainWorkspaceRepository

/** Generic compare-and-set storage for Phase 6 domain drafts. SQLCipher encrypts the complete DB file. */
class RoomDomainWorkspaceRepository(private val database: AppDatabase) : DomainWorkspaceRepository {
    private val dao = database.domainWorkspaceDao()

    override suspend fun create(
        id: String,
        domain: String,
        schemaVersion: Int,
        status: String,
        payload: ByteArray,
        nowEpochMillis: Long
    ): DomainWorkspaceRecord {
        val hash = DomainWorkspaceRecord.hex(DomainWorkspaceRecord.sha256(payload))
        // Validate the entire record before touching the database so bad timestamps/keys/statuses
        // cannot leave a row behind when the return value fails to construct.
        val record = DomainWorkspaceRecord(
            id = id, domain = domain, schemaVersion = schemaVersion, revision = 1,
            status = status, payload = payload, payloadSha256 = hash,
            createdAtEpochMillis = nowEpochMillis, updatedAtEpochMillis = nowEpochMillis
        )
        dao.insert(DomainWorkspaceEntity(
            id = record.id, domain = record.domain, schemaVersion = record.schemaVersion, revision = record.revision,
            status = record.status, payload = record.payload, payloadSha256 = record.payloadSha256,
            createdAtEpochMillis = record.createdAtEpochMillis, updatedAtEpochMillis = record.updatedAtEpochMillis
        ))
        return record
    }

    override suspend fun read(id: String): DomainWorkspaceRecord? = dao.get(id)?.toRecord()

    override suspend fun list(domain: String, limit: Int): List<DomainWorkspaceRecord> {
        require(limit in 1..500)
        return dao.list(domain, limit).map { it.toRecord() }
    }

    override suspend fun update(
        id: String,
        expectedRevision: Long,
        status: String,
        payload: ByteArray,
        nowEpochMillis: Long
    ): Boolean {
        require(payload.isNotEmpty() && payload.size <= DomainWorkspaceRecord.MAX_PAYLOAD_BYTES)
        val current = dao.get(id) ?: return false
        require(nowEpochMillis >= current.updatedAtEpochMillis) { "Workspace update timestamp must be monotonic" }
        val hash = DomainWorkspaceRecord.hex(DomainWorkspaceRecord.sha256(payload))
        // Constructing this candidate validates status, domain metadata, time and checksum before CAS.
        DomainWorkspaceRecord(
            id = current.id, domain = current.domain, schemaVersion = current.schemaVersion,
            revision = Math.addExact(current.revision, 1), status = status, payload = payload,
            payloadSha256 = hash, createdAtEpochMillis = current.createdAtEpochMillis,
            updatedAtEpochMillis = nowEpochMillis
        )
        return dao.updateIfRevisionMatches(id, expectedRevision, status, payload.copyOf(), hash, nowEpochMillis) == 1
    }

    override suspend fun delete(id: String, expectedRevision: Long): Boolean =
        dao.deleteIfRevisionMatches(id, expectedRevision) == 1

    private fun DomainWorkspaceEntity.toRecord(): DomainWorkspaceRecord = DomainWorkspaceRecord(
        id = id, domain = domain, schemaVersion = schemaVersion, revision = revision,
        status = status, payload = payload, payloadSha256 = payloadSha256,
        createdAtEpochMillis = createdAtEpochMillis, updatedAtEpochMillis = updatedAtEpochMillis
    )
}
