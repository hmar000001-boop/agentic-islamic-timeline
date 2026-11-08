package app.timeline.islamic.core.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "timeline_item", indices = [Index("startsAtEpochMillis"), Index("status")])
data class TimelineItemEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long?,
    val zoneId: String,
    val origin: String,
    val status: String,
    val sourceVersion: String?
)

@Entity(tableName = "context_event", indices = [Index("occurredAtEpochMillis"), Index("eventType")])
data class ContextEventEntity(
    @PrimaryKey val id: String,
    val eventType: String,
    val occurredAtEpochMillis: Long,
    val encryptedPayload: ByteArray,
    val provenance: String
)

@Entity(tableName = "agent_task", indices = [Index("status"), Index("nextEligibleAtEpochMillis")])
data class AgentTaskEntity(
    @PrimaryKey val id: String,
    val goalType: String,
    val status: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val stateVersion: Long,
    val nextEligibleAtEpochMillis: Long?
)

@Entity(tableName = "agent_task_step", foreignKeys = [ForeignKey(entity = AgentTaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)], indices = [Index("taskId"), Index(value = ["taskId", "sequence"], unique = true)])
data class AgentTaskStepEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val sequence: Int,
    val stepType: String,
    val status: String,
    val inputHash: String,
    val outputRef: String?,
    val attempt: Int,
    val idempotencyKey: String?
)

@Entity(tableName = "agent_task_checkpoint", foreignKeys = [ForeignKey(entity = AgentTaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)], indices = [Index("taskId"), Index(value = ["taskId", "checkpointVersion"], unique = true)])
data class AgentTaskCheckpointEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val checkpointVersion: Long,
    val currentStepId: String?,
    val stateSchemaVersion: Int,
    val encryptedPayload: ByteArray,
    val payloadHash: String,
    val createdAtEpochMillis: Long
)

@Entity(tableName = "action_proposal", foreignKeys = [ForeignKey(entity = AgentTaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)], indices = [Index("taskId"), Index("expiresAtEpochMillis")])
data class ActionProposalEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val actionType: String,
    val targetId: String?,
    val canonicalParametersEncrypted: ByteArray,
    val parametersSha256: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val impactClass: String,
    val reversibility: String
)

@Entity(tableName = "pending_approval", foreignKeys = [ForeignKey(entity = ActionProposalEntity::class, parentColumns = ["id"], childColumns = ["proposalId"], onDelete = ForeignKey.CASCADE)], indices = [Index("proposalId"), Index("expiresAtEpochMillis")])
data class PendingApprovalEntity(
    @PrimaryKey val id: String,
    val proposalId: String,
    val proposalHash: String,
    val state: String,
    val expiresAtEpochMillis: Long,
    val decisionAtEpochMillis: Long?,
    val sourceGrantId: String? = null
)

@Entity(tableName = "permission_grant", indices = [Index("actionType"), Index("expiresAtEpochMillis")])
data class PermissionGrantEntity(
    @PrimaryKey val id: String,
    val actionType: String,
    val scopeEncrypted: ByteArray,
    val constraintsEncrypted: ByteArray,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val revokedAtEpochMillis: Long?,
    val policyVersion: String
)

@Entity(tableName = "canvas_session", indices = [Index("status"), Index("updatedAtEpochMillis")])
data class CanvasSessionEntity(
    @PrimaryKey val id: String,
    val domain: String,
    val status: String,
    val encryptedState: ByteArray,
    val currentStep: String?,
    val version: Long,
    val stateSchemaVersion: Int,
    val updatedAtEpochMillis: Long
)

@Entity(tableName = "canvas_snapshot", foreignKeys = [ForeignKey(entity = CanvasSessionEntity::class, parentColumns = ["id"], childColumns = ["canvasId"], onDelete = ForeignKey.CASCADE)], indices = [Index("canvasId"), Index(value = ["canvasId", "snapshotVersion"], unique = true)])
data class CanvasSnapshotEntity(
    @PrimaryKey val id: String,
    val canvasId: String,
    val snapshotVersion: Long,
    val encryptedPayload: ByteArray,
    val payloadHash: String,
    val createdAtEpochMillis: Long
)

@Entity(tableName = "audit_event", indices = [Index("sequence", unique = true), Index("timestampEpochMillis")])
data class AuditEventEntity(
    @PrimaryKey val id: String,
    val sequence: Long,
    val eventType: String,
    val timestampEpochMillis: Long,
    val payloadHash: String,
    val previousMac: String?,
    val recordMac: String
)


@Entity(tableName = "canvas_artifact", foreignKeys = [ForeignKey(entity = CanvasSessionEntity::class, parentColumns = ["id"], childColumns = ["canvasId"], onDelete = ForeignKey.CASCADE)], indices = [Index("canvasId"), Index("sha256", unique = true)])
data class CanvasArtifactEntity(
    @PrimaryKey val id: String,
    val canvasId: String,
    val artifactType: String,
    val encryptedPath: String,
    val sha256: String,
    val retentionPolicy: String,
    val createdAtEpochMillis: Long
)

@Entity(tableName = "action_outbox", foreignKeys = [ForeignKey(entity = ActionProposalEntity::class, parentColumns = ["id"], childColumns = ["proposalId"], onDelete = ForeignKey.CASCADE)], indices = [Index("proposalId"), Index("state"), Index("idempotencyKey", unique = true)])
data class ActionOutboxEntity(
    @PrimaryKey val id: String,
    val proposalId: String,
    val actionHash: String,
    val idempotencyKey: String,
    val state: String,
    val attempt: Int,
    val externalRef: String?,
    val encryptedReceipt: ByteArray?,
    val updatedAtEpochMillis: Long
)

@Entity(tableName = "action_execution", indices = [Index("proposalHash"), Index("proposalId", unique = true), Index("idempotencyKey", unique = true), Index("state")])
data class ActionExecutionEntity(
    @PrimaryKey val id: String,
    val proposalId: String?,
    val proposalHash: String,
    val idempotencyKey: String,
    val state: String,
    val encryptedReceipt: ByteArray?,
    val undoState: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(tableName = "user_preference")
data class UserPreferenceEntity(
    @PrimaryKey val key: String,
    val encryptedValue: ByteArray,
    val version: Long,
    val updatedAtEpochMillis: Long
)

@Entity(tableName = "hifz_plan", indices = [Index("planVersion")])
data class HifzPlanEntity(
    @PrimaryKey val id: String,
    val mushafEditionId: String,
    val planVersion: Long,
    val encryptedPreferences: ByteArray,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(tableName = "hifz_review", foreignKeys = [ForeignKey(entity = HifzPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)], indices = [Index("planId"), Index("reviewedAtEpochMillis")])
data class HifzReviewEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val passageRef: String,
    val reviewedAtEpochMillis: Long,
    val outcome: String,
    val notesEncrypted: ByteArray?
)

@Entity(tableName = "prayer_profile")
data class PrayerProfileEntity(
    @PrimaryKey val id: String,
    val calculationMethod: String,
    val fajrAngle: Double?,
    val ishaAngle: Double?,
    val asrRule: String,
    val highLatitudeRule: String,
    val zoneId: String,
    val updatedAtEpochMillis: Long
)

@Entity(tableName = "document_attachment", indices = [Index("sha256", unique = true)])
data class DocumentAttachmentEntity(
    @PrimaryKey val id: String,
    val encryptedPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val retentionPolicy: String,
    val importedAtEpochMillis: Long
)

@Entity(tableName = "installed_corpus_pack", indices = [Index("status"), Index(value = ["packId", "version"], unique = true)])
data class InstalledCorpusPackEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val version: String,
    val manifestHash: String,
    val installedAtEpochMillis: Long,
    val status: String,
    val licenseProfile: String
)

@Entity(tableName = "installed_model", indices = [Index("status"), Index(value = ["modelId", "version"], unique = true)])
data class InstalledModelEntity(
    @PrimaryKey val id: String,
    val modelId: String,
    val version: String,
    val fileHash: String,
    val runtimeFormat: String,
    val status: String,
    val installedAtEpochMillis: Long
)


@Entity(tableName = "agent_task_lease", foreignKeys = [ForeignKey(entity = AgentTaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)], indices = [Index("expiresAtEpochMillis")])
data class AgentTaskLeaseEntity(
    @PrimaryKey val taskId: String,
    val leaseOwner: String,
    val acquiredAtEpochMillis: Long,
    val heartbeatAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val fencingToken: Long
)

@Entity(tableName = "agent_task_event", foreignKeys = [ForeignKey(entity = AgentTaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)], indices = [Index("taskId"), Index(value = ["taskId", "sequence"], unique = true)])
data class AgentTaskEventEntity(
    @PrimaryKey val eventId: String,
    val taskId: String,
    val sequence: Long,
    val eventType: String,
    val encryptedPayload: ByteArray,
    val occurredAtEpochMillis: Long
)

@Entity(tableName = "canvas_step", foreignKeys = [ForeignKey(entity = CanvasSessionEntity::class, parentColumns = ["id"], childColumns = ["canvasId"], onDelete = ForeignKey.CASCADE)], indices = [Index("canvasId"), Index(value = ["canvasId", "stepKey"], unique = true)])
data class CanvasStepEntity(
    @PrimaryKey val id: String,
    val canvasId: String,
    val stepKey: String,
    val status: String,
    val inputRef: String?,
    val outputRef: String?,
    val encryptedEvidenceIds: ByteArray,
    val stepVersion: Long,
    val updatedAtEpochMillis: Long
)

/** Stored only inside the SQLCipher database. The signature covers the exact canonical payload bytes. */
@Entity(tableName = "publisher_keyring_state")
data class PublisherKeyringStateEntity(
    @PrimaryKey val id: String,
    val version: Long,
    val payload: ByteArray,
    val signature: ByteArray,
    val installedAtEpochMillis: Long
)


/** Embeddings are derived from source passages and stay in the SQLCipher-encrypted Room database. */
@Entity(
    tableName = "dense_index_generation",
    indices = [Index(value = ["packId", "packVersion", "modelId", "modelVersion", "state"]), Index("state")]
)
data class DenseIndexGenerationEntity(
    @PrimaryKey val generationId: String,
    val packId: String,
    val packVersion: String,
    val modelId: String,
    val modelVersion: String,
    val modelSha256: String,
    val dimension: Int,
    val vectorCount: Long,
    val encodedBytes: Long,
    val maximumEncodedBytes: Long,
    val state: String,
    val createdAtEpochMillis: Long
)

@Entity(
    tableName = "dense_index_vector",
    primaryKeys = ["generationId", "stableEvidenceKey"],
    foreignKeys = [ForeignKey(
        entity = DenseIndexGenerationEntity::class,
        parentColumns = ["generationId"],
        childColumns = ["generationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("generationId")]
)
data class DenseIndexVectorEntity(
    val generationId: String,
    val stableEvidenceKey: String,
    val vector: ByteArray
)
