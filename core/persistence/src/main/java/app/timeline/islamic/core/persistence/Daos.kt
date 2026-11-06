package app.timeline.islamic.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao interface TimelineDao {
    @Query("SELECT * FROM timeline_item WHERE startsAtEpochMillis >= :from AND startsAtEpochMillis < :until ORDER BY startsAtEpochMillis ASC")
    fun observeBetween(from: Long, until: Long): Flow<List<TimelineItemEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: TimelineItemEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: TimelineItemEntity)
    @Query("DELETE FROM timeline_item WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao interface AgentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTask(task: AgentTaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTask(task: AgentTaskEntity)
    @Query("SELECT * FROM agent_task WHERE id = :id") suspend fun getTask(id: String): AgentTaskEntity?
    @Query("SELECT * FROM agent_task WHERE status IN (:statuses) ORDER BY createdAtEpochMillis") fun observeTasks(statuses: List<String>): Flow<List<AgentTaskEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertStep(step: AgentTaskStepEntity)
    @Query("SELECT * FROM agent_task_step WHERE taskId = :taskId ORDER BY sequence") suspend fun getSteps(taskId: String): List<AgentTaskStepEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCheckpoint(checkpoint: AgentTaskCheckpointEntity)
    @Query("SELECT * FROM agent_task_checkpoint WHERE taskId = :taskId ORDER BY checkpointVersion DESC LIMIT 1") suspend fun latestCheckpoint(taskId: String): AgentTaskCheckpointEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun appendTaskEvent(event: AgentTaskEventEntity)
    @Query("SELECT * FROM agent_task_event WHERE taskId = :taskId ORDER BY sequence") suspend fun taskEvents(taskId: String): List<AgentTaskEventEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun createLeaseIfAbsent(lease: AgentTaskLeaseEntity): Long
    @Query("SELECT * FROM agent_task_lease WHERE taskId = :taskId") suspend fun lease(taskId: String): AgentTaskLeaseEntity?
    @Query("UPDATE agent_task_lease SET leaseOwner = :owner, acquiredAtEpochMillis = :now, heartbeatAtEpochMillis = :now, expiresAtEpochMillis = :expiresAt, fencingToken = fencingToken + 1 WHERE taskId = :taskId AND expiresAtEpochMillis <= :now") suspend fun takeExpiredLease(taskId: String, owner: String, now: Long, expiresAt: Long): Int
}

@Dao interface AuthorizationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertProposal(proposal: ActionProposalEntity)
    @Query("SELECT * FROM action_proposal WHERE id = :id") suspend fun proposal(id: String): ActionProposalEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertApproval(approval: PendingApprovalEntity)
    @Query("SELECT * FROM pending_approval WHERE proposalId = :proposalId ORDER BY expiresAtEpochMillis DESC") suspend fun approvals(proposalId: String): List<PendingApprovalEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGrant(grant: PermissionGrantEntity)
    @Query("SELECT * FROM permission_grant WHERE id = :grantId") suspend fun grant(grantId: String): PermissionGrantEntity?
    @Query("UPDATE permission_grant SET revokedAtEpochMillis = :revokedAt WHERE id = :grantId AND revokedAtEpochMillis IS NULL") suspend fun revokeGrant(grantId: String, revokedAt: Long): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExecution(execution: ActionExecutionEntity)
    @Query("SELECT * FROM action_execution WHERE id = :id") suspend fun execution(id: String): ActionExecutionEntity?
}

@Dao interface CanvasDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSession(session: CanvasSessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(session: CanvasSessionEntity)
    @Query("SELECT * FROM canvas_session WHERE id = :id") suspend fun getSession(id: String): CanvasSessionEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSnapshot(snapshot: CanvasSnapshotEntity)
    @Query("SELECT * FROM canvas_snapshot WHERE canvasId = :canvasId ORDER BY snapshotVersion DESC LIMIT 1") suspend fun latestSnapshot(canvasId: String): CanvasSnapshotEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertStep(step: CanvasStepEntity)
    @Query("SELECT * FROM canvas_step WHERE canvasId = :canvasId ORDER BY stepKey") suspend fun steps(canvasId: String): List<CanvasStepEntity>
}

@Dao interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun append(event: AuditEventEntity)
    @Query("SELECT * FROM audit_event ORDER BY sequence DESC LIMIT :limit") suspend fun latest(limit: Int): List<AuditEventEntity>
}

@Dao interface ContextEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun append(event: ContextEventEntity)
    @Query("SELECT * FROM context_event ORDER BY occurredAtEpochMillis DESC LIMIT :limit") suspend fun latest(limit: Int): List<ContextEventEntity>
}


@Dao interface ActionOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun prepare(item: ActionOutboxEntity)
    @Query("SELECT * FROM action_outbox WHERE state IN (:states) ORDER BY updatedAtEpochMillis") suspend fun byStates(states: List<String>): List<ActionOutboxEntity>
    @Query("UPDATE action_outbox SET state = :state, attempt = attempt + 1, updatedAtEpochMillis = :updatedAt WHERE id = :id") suspend fun transition(id: String, state: String, updatedAt: Long): Int
}

@Dao interface UserDomainDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPreference(item: UserPreferenceEntity)
    @Query("SELECT * FROM user_preference WHERE `key` = :key") suspend fun preference(key: String): UserPreferenceEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putPrayerProfile(profile: PrayerProfileEntity)
    @Query("SELECT * FROM prayer_profile ORDER BY updatedAtEpochMillis DESC") suspend fun prayerProfiles(): List<PrayerProfileEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putHifzPlan(plan: HifzPlanEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putHifzReview(review: HifzReviewEntity)
    @Query("SELECT * FROM hifz_review WHERE planId = :planId ORDER BY reviewedAtEpochMillis DESC") suspend fun hifzReviews(planId: String): List<HifzReviewEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putAttachment(attachment: DocumentAttachmentEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCanvasArtifact(artifact: CanvasArtifactEntity)
}

@Dao interface InstalledArtifactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCorpusPack(pack: InstalledCorpusPackEntity)
    @Query("SELECT * FROM installed_corpus_pack ORDER BY installedAtEpochMillis DESC") suspend fun corpusPacks(): List<InstalledCorpusPackEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putModel(model: InstalledModelEntity)
    @Query("SELECT * FROM installed_model ORDER BY installedAtEpochMillis DESC") suspend fun models(): List<InstalledModelEntity>
}
