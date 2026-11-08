package app.timeline.islamic.core.persistence

import app.timeline.islamic.core.knowledge.CorpusPackRegistry
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord

/** Room-backed registry adapter for already validated, signed corpus packs. */
class RoomCorpusPackRegistry(private val dao: InstalledArtifactDao) : CorpusPackRegistry {
    override suspend fun isInstalled(packId: String, version: String): Boolean =
        dao.corpusPack(packId, version)?.status == InstalledCorpusPackRecord.STATUS_INSTALLED

    override suspend fun register(record: InstalledCorpusPackRecord) {
        require(record.packId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")))
        require(record.version.matches(Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?")))
        dao.insertCorpusPack(
            InstalledCorpusPackEntity(
                id = record.stableId,
                packId = record.packId,
                version = record.version,
                manifestHash = record.manifestSha256,
                installedAtEpochMillis = record.installedAtEpochMillis,
                status = record.status,
                licenseProfile = record.licenseProfile
            )
        )
    }

    override suspend fun listInstalled(): List<InstalledCorpusPackRecord> = dao.corpusPacks().map { row ->
        InstalledCorpusPackRecord(
            packId = row.packId,
            version = row.version,
            manifestSha256 = row.manifestHash,
            installedAtEpochMillis = row.installedAtEpochMillis,
            licenseProfile = row.licenseProfile,
            status = row.status
        )
    }
}
