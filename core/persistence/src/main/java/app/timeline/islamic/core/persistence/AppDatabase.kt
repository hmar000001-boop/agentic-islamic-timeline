package app.timeline.islamic.core.persistence

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TimelineItemEntity::class,
        ContextEventEntity::class,
        AgentTaskEntity::class,
        AgentTaskStepEntity::class,
        AgentTaskCheckpointEntity::class,
        ActionProposalEntity::class,
        PendingApprovalEntity::class,
        PermissionGrantEntity::class,
        CanvasSessionEntity::class,
        CanvasSnapshotEntity::class,
        AuditEventEntity::class,
        CanvasArtifactEntity::class,
        ActionOutboxEntity::class,
        ActionExecutionEntity::class,
        UserPreferenceEntity::class,
        HifzPlanEntity::class,
        HifzReviewEntity::class,
        PrayerProfileEntity::class,
        DocumentAttachmentEntity::class,
        InstalledCorpusPackEntity::class,
        InstalledModelEntity::class,
        AgentTaskLeaseEntity::class,
        AgentTaskEventEntity::class,
        CanvasStepEntity::class,
        PublisherKeyringStateEntity::class,
        DenseIndexGenerationEntity::class,
        DenseIndexVectorEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timelineDao(): TimelineDao
    abstract fun agentDao(): AgentDao
    abstract fun authorizationDao(): AuthorizationDao
    abstract fun canvasDao(): CanvasDao
    abstract fun auditDao(): AuditDao
    abstract fun contextEventDao(): ContextEventDao
    abstract fun actionOutboxDao(): ActionOutboxDao
    abstract fun userDomainDataDao(): UserDomainDataDao
    abstract fun installedArtifactDao(): InstalledArtifactDao
    abstract fun publisherKeyringDao(): PublisherKeyringDao
    abstract fun denseIndexDao(): DenseIndexDao
}
