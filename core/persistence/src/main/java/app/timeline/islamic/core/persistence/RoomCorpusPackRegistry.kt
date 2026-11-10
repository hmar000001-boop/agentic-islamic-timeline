package app.timeline.islamic.core.persistence

import androidx.room.withTransaction
import app.timeline.islamic.core.knowledge.CorpusPackRegistry
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord

/** Room-backed registry adapter. Multi-row lifecycle transitions execute in a single SQL transaction. */
class RoomCorpusPackRegistry(private val database: AppDatabase) : CorpusPackRegistry {
    private val dao get() = database.installedArtifactDao()

    override suspend fun isInstalled(packId: String, version: String): Boolean =
        dao.corpusPack(packId, version)?.status == InstalledCorpusPackRecord.STATUS_INSTALLED

    override suspend fun register(record: InstalledCorpusPackRecord) {
        validateRecord(record)
        require(record.status == InstalledCorpusPackRecord.STATUS_INSTALLED) { "New corpus records must enter through INSTALLED" }
        dao.insertCorpusPack(record.toEntity())
    }

    override suspend fun listInstalled(): List<InstalledCorpusPackRecord> = dao.corpusPacks().map { it.toRecord() }

    override suspend fun listAll(): List<InstalledCorpusPackRecord> = dao.allCorpusPacks().map { it.toRecord() }

    override suspend fun activateReplacement(expectedCurrentVersion: String, replacement: InstalledCorpusPackRecord): Boolean {
        validateRecord(replacement)
        if (replacement.status != InstalledCorpusPackRecord.STATUS_INSTALLED) return false
        return database.withTransaction {
            val current = dao.corpusPack(replacement.packId, expectedCurrentVersion) ?: return@withTransaction false
            if (current.status != InstalledCorpusPackRecord.STATUS_INSTALLED) return@withTransaction false
            if (dao.countActiveCorpusPacks(replacement.packId) != 1) return@withTransaction false
            if (replacement.version == expectedCurrentVersion) return@withTransaction false
            if (dao.supersedeCorpusPack(replacement.packId, expectedCurrentVersion) != 1) return@withTransaction false
            dao.insertCorpusPack(replacement.toEntity())
            true
        }
    }

    override suspend fun transitionStatus(stableId: String, expectedStatus: String, newStatus: String): Boolean {
        val allowed = when (expectedStatus) {
            InstalledCorpusPackRecord.STATUS_INSTALLED -> setOf(
                InstalledCorpusPackRecord.STATUS_QUARANTINED,
                InstalledCorpusPackRecord.STATUS_REMOVAL_PENDING
            )
            InstalledCorpusPackRecord.STATUS_QUARANTINED -> setOf(
                InstalledCorpusPackRecord.STATUS_INSTALLED,
                InstalledCorpusPackRecord.STATUS_REMOVAL_PENDING
            )
            InstalledCorpusPackRecord.STATUS_SUPERSEDED -> setOf(InstalledCorpusPackRecord.STATUS_REMOVAL_PENDING)
            else -> emptySet()
        }
        require(newStatus in allowed) { "Illegal corpus lifecycle transition: $expectedStatus -> $newStatus" }
        return dao.transitionCorpusPackStatus(stableId, expectedStatus, newStatus) == 1
    }

    override suspend fun removeRecord(stableId: String): Boolean = dao.deleteCorpusPack(stableId) == 1

    private fun validateRecord(record: InstalledCorpusPackRecord) {
        require(record.packId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")))
        require(record.version.matches(Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?")))
        require(record.manifestSha256.matches(Regex("[a-f0-9]{64}")))
        require(record.status in setOf(
            InstalledCorpusPackRecord.STATUS_INSTALLED,
            InstalledCorpusPackRecord.STATUS_QUARANTINED,
            InstalledCorpusPackRecord.STATUS_SUPERSEDED,
            InstalledCorpusPackRecord.STATUS_REMOVAL_PENDING
        ))
    }

    private fun InstalledCorpusPackRecord.toEntity() = InstalledCorpusPackEntity(
        id = stableId,
        packId = packId,
        version = version,
        manifestHash = manifestSha256,
        installedAtEpochMillis = installedAtEpochMillis,
        status = status,
        licenseProfile = licenseProfile
    )

    private fun InstalledCorpusPackEntity.toRecord() = InstalledCorpusPackRecord(
        packId = packId,
        version = version,
        manifestSha256 = manifestHash,
        installedAtEpochMillis = installedAtEpochMillis,
        licenseProfile = licenseProfile,
        status = status
    )
}
