package app.timeline.islamic.core.models

import app.timeline.islamic.core.persistence.AppDatabase
import app.timeline.islamic.core.persistence.InstalledModelEntity

/** Stable model identity is derived from the built-in catalog key and immutable revision. */
data class InstalledModelRecord(
    val recordId: String,
    val modelId: String,
    val revision: String,
    val sha256: String,
    val runtimeFormat: String,
    val status: ModelLifecycleStatus,
    val installedAtEpochMillis: Long
)

interface ModelRegistry {
    suspend fun find(modelId: String, revision: String): InstalledModelRecord?
    suspend fun list(): List<InstalledModelRecord>
    suspend fun insertInstalling(record: InstalledModelRecord)
    suspend fun transition(recordId: String, expected: ModelLifecycleStatus, next: ModelLifecycleStatus): Boolean
    suspend fun deleteAfterFilesRemoved(recordId: String): Boolean
}

/** Adapter over the existing SQLCipher-protected installed_model table; schema v5 is unchanged. */
class RoomModelRegistry(private val database: AppDatabase) : ModelRegistry {
    private val dao get() = database.installedArtifactDao()

    override suspend fun find(modelId: String, revision: String): InstalledModelRecord? =
        dao.model(modelId, revision)?.toRecord()

    override suspend fun list(): List<InstalledModelRecord> {
        // Unknown persisted lifecycle values are not treated as installable; normalize them to
        // QUARANTINED before returning any inventory to the UI or recovery code.
        dao.quarantineUnknownModelStatuses()
        return dao.models().map { entity -> entity.toRecord() }
    }

    override suspend fun insertInstalling(record: InstalledModelRecord) {
        require(record.status == ModelLifecycleStatus.INSTALLING)
        dao.insertModel(record.toEntity())
    }

    override suspend fun transition(
        recordId: String,
        expected: ModelLifecycleStatus,
        next: ModelLifecycleStatus
    ): Boolean {
        require(isLegalTransition(expected, next)) { "Illegal model lifecycle transition: $expected -> $next" }
        return dao.transitionModelStatus(recordId, expected.persisted, next.persisted) == 1
    }

    override suspend fun deleteAfterFilesRemoved(recordId: String): Boolean =
        dao.deleteModelAfterRemoval(recordId) == 1

    private fun InstalledModelRecord.toEntity() = InstalledModelEntity(
        id = recordId,
        modelId = modelId,
        version = revision,
        fileHash = sha256,
        runtimeFormat = runtimeFormat,
        status = status.persisted,
        installedAtEpochMillis = installedAtEpochMillis
    )

    private fun InstalledModelEntity.toRecord(): InstalledModelRecord {
        val parsed = ModelLifecycleStatus.parse(status) ?: ModelLifecycleStatus.QUARANTINED
        return InstalledModelRecord(id, modelId, version, fileHash, runtimeFormat, parsed, installedAtEpochMillis)
    }

    private fun isLegalTransition(from: ModelLifecycleStatus, to: ModelLifecycleStatus): Boolean = when (from) {
        ModelLifecycleStatus.INSTALLING -> to in setOf(ModelLifecycleStatus.INSTALLED, ModelLifecycleStatus.QUARANTINED, ModelLifecycleStatus.REMOVAL_PENDING)
        ModelLifecycleStatus.INSTALLED -> to in setOf(ModelLifecycleStatus.QUARANTINED, ModelLifecycleStatus.REMOVAL_PENDING)
        ModelLifecycleStatus.QUARANTINED -> to == ModelLifecycleStatus.REMOVAL_PENDING
        ModelLifecycleStatus.REMOVAL_PENDING -> false
    }
}

internal fun stableModelRecordId(spec: ModelArtifactSpec): String =
    sha256Hex("${spec.id}@${spec.revision}".toByteArray(Charsets.UTF_8))

internal fun sha256Hex(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
