package app.timeline.islamic.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao interface TimelineDao {
    @Query("SELECT * FROM timeline_item WHERE startsAtEpochMillis < :until AND (endsAtEpochMillis IS NULL OR endsAtEpochMillis > :from) ORDER BY startsAtEpochMillis ASC")
    fun observeBetween(from: Long, until: Long): Flow<List<TimelineItemEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: TimelineItemEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: TimelineItemEntity)
    @Query("DELETE FROM timeline_item WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao interface AgentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTask(task: AgentTaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTask(task: AgentTaskEntity)
    @Query("SELECT * FROM agent_task WHERE id = :id") suspend fun getTask(id: String): AgentTaskEntity?
    @Query("UPDATE agent_task SET status = :newStatus, stateVersion = stateVersion + 1, updatedAtEpochMillis = :now WHERE id = :taskId AND status = :expectedStatus AND stateVersion = :expectedVersion")
    suspend fun transitionTask(taskId: String, expectedStatus: String, expectedVersion: Long, newStatus: String, now: Long): Int
    @Query("SELECT * FROM agent_task WHERE status IN (:statuses) ORDER BY createdAtEpochMillis") fun observeTasks(statuses: List<String>): Flow<List<AgentTaskEntity>>
    @Query("SELECT agent_task.* FROM agent_task INNER JOIN agent_task_lease ON agent_task.id = agent_task_lease.taskId WHERE agent_task.status = 'RUNNING' AND agent_task_lease.expiresAtEpochMillis <= :now") suspend fun runningTasksWithExpiredLease(now: Long): List<AgentTaskEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertStep(step: AgentTaskStepEntity)
    @Query("SELECT * FROM agent_task_step WHERE taskId = :taskId ORDER BY sequence") suspend fun getSteps(taskId: String): List<AgentTaskStepEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCheckpoint(checkpoint: AgentTaskCheckpointEntity)
    @Query("SELECT * FROM agent_task_checkpoint WHERE taskId = :taskId ORDER BY checkpointVersion DESC LIMIT 1") suspend fun latestCheckpoint(taskId: String): AgentTaskCheckpointEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun appendTaskEvent(event: AgentTaskEventEntity)
    @Query("SELECT * FROM agent_task_event WHERE taskId = :taskId ORDER BY sequence") suspend fun taskEvents(taskId: String): List<AgentTaskEventEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun createLeaseIfAbsent(lease: AgentTaskLeaseEntity): Long
    @Query("SELECT * FROM agent_task_lease WHERE taskId = :taskId") suspend fun lease(taskId: String): AgentTaskLeaseEntity?
    @Query("UPDATE agent_task_lease SET heartbeatAtEpochMillis = :now, expiresAtEpochMillis = :expiresAt WHERE taskId = :taskId AND leaseOwner = :owner AND fencingToken = :fencingToken AND acquiredAtEpochMillis <= :now AND heartbeatAtEpochMillis <= :now AND expiresAtEpochMillis > :now")
    suspend fun heartbeatLease(taskId: String, owner: String, fencingToken: Long, now: Long, expiresAt: Long): Int
    @Query("UPDATE agent_task_lease SET expiresAtEpochMillis = :now WHERE taskId = :taskId AND leaseOwner = :owner AND fencingToken = :fencingToken AND acquiredAtEpochMillis <= :now AND heartbeatAtEpochMillis <= :now AND expiresAtEpochMillis > :now")
    suspend fun releaseLease(taskId: String, owner: String, fencingToken: Long, now: Long): Int
    @Query("UPDATE agent_task_lease SET expiresAtEpochMillis = :now WHERE taskId = :taskId")
    suspend fun expireLeaseForTask(taskId: String, now: Long): Int
    @Query("SELECT COALESCE(MAX(sequence), 0) FROM agent_task_event WHERE taskId = :taskId")
    suspend fun maxEventSequence(taskId: String): Long
    @Query("UPDATE agent_task_lease SET leaseOwner = :owner, acquiredAtEpochMillis = :now, heartbeatAtEpochMillis = :now, expiresAtEpochMillis = :expiresAt, fencingToken = fencingToken + 1 WHERE taskId = :taskId AND acquiredAtEpochMillis <= :now AND heartbeatAtEpochMillis <= :now AND expiresAtEpochMillis <= :now") suspend fun takeExpiredLease(taskId: String, owner: String, now: Long, expiresAt: Long): Int
}

@Dao interface AuthorizationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertProposal(proposal: ActionProposalEntity)
    @Query("SELECT * FROM action_proposal WHERE id = :id") suspend fun proposal(id: String): ActionProposalEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertApproval(approval: PendingApprovalEntity)
    @Query("SELECT * FROM pending_approval WHERE proposalId = :proposalId ORDER BY expiresAtEpochMillis DESC") suspend fun approvals(proposalId: String): List<PendingApprovalEntity>
    @Query("SELECT * FROM pending_approval WHERE state = 'PENDING' AND expiresAtEpochMillis <= :now") suspend fun expiredPendingApprovals(now: Long): List<PendingApprovalEntity>
    @Query("UPDATE pending_approval SET state = 'EXPIRED', decisionAtEpochMillis = :now WHERE id = :approvalId AND state = 'PENDING' AND expiresAtEpochMillis <= :now") suspend fun expireApproval(approvalId: String, now: Long): Int
    @Query("UPDATE pending_approval SET state = :newState, decisionAtEpochMillis = :decisionAt, expiresAtEpochMillis = :newExpiresAt WHERE id = :approvalId AND proposalHash = :proposalHash AND state = :expectedState AND expiresAtEpochMillis > :decisionAt")
    suspend fun decideApproval(approvalId: String, proposalHash: String, expectedState: String, newState: String, decisionAt: Long, newExpiresAt: Long): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGrant(grant: PermissionGrantEntity)
    @Query("SELECT * FROM permission_grant WHERE id = :grantId") suspend fun grant(grantId: String): PermissionGrantEntity?
    @Query("SELECT * FROM permission_grant WHERE actionType = :actionType ORDER BY createdAtEpochMillis DESC") suspend fun grantsForAction(actionType: String): List<PermissionGrantEntity>
    @Query("UPDATE permission_grant SET revokedAtEpochMillis = :revokedAt WHERE id = :grantId AND revokedAtEpochMillis IS NULL") suspend fun revokeGrant(grantId: String, revokedAt: Long): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExecution(execution: ActionExecutionEntity)
    @Query("SELECT * FROM action_execution WHERE id = :id") suspend fun execution(id: String): ActionExecutionEntity?
    @Query("SELECT * FROM action_execution WHERE proposalId = :proposalId LIMIT 1") suspend fun executionForProposal(proposalId: String): ActionExecutionEntity?
    @Query("SELECT * FROM action_execution WHERE state = :state ORDER BY createdAtEpochMillis") suspend fun executionsByState(state: String): List<ActionExecutionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOutbox(item: ActionOutboxEntity)
    @Query("UPDATE action_execution SET state = :state, encryptedReceipt = :receipt, updatedAtEpochMillis = :now WHERE id = :executionId AND state IN ('EXECUTING', 'CLAIMED')") suspend fun finishExecution(executionId: String, state: String, receipt: ByteArray?, now: Long): Int
    @Query("UPDATE action_execution SET state = 'EXECUTING', updatedAtEpochMillis = :now WHERE id = :executionId AND state = 'CLAIMED' AND proposalHash = :proposalHash AND idempotencyKey = :idempotencyKey") suspend fun startExecution(executionId: String, proposalHash: String, idempotencyKey: String, now: Long): Int
    @Query("UPDATE action_outbox SET state = 'EXECUTING', updatedAtEpochMillis = :now WHERE id = :executionId AND state = 'CLAIMED' AND actionHash = :proposalHash AND idempotencyKey = :idempotencyKey") suspend fun startOutbox(executionId: String, proposalHash: String, idempotencyKey: String, now: Long): Int
    @Query("UPDATE action_outbox SET state = :state, encryptedReceipt = :receipt, updatedAtEpochMillis = :now WHERE id = :executionId AND state IN ('EXECUTING', 'CLAIMED')") suspend fun finishOutbox(executionId: String, state: String, receipt: ByteArray?, now: Long): Int
}

@Dao interface CanvasDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSession(session: CanvasSessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(session: CanvasSessionEntity)
    @Query("SELECT * FROM canvas_session WHERE id = :id") suspend fun getSession(id: String): CanvasSessionEntity?
    @Query("SELECT * FROM canvas_session ORDER BY updatedAtEpochMillis DESC") fun observeSessions(): Flow<List<CanvasSessionEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSnapshot(snapshot: CanvasSnapshotEntity)
    @Query("UPDATE canvas_session SET domain = :domain, status = :status, encryptedState = :state, currentStep = :currentStep, version = :newVersion, updatedAtEpochMillis = :updatedAt WHERE id = :id AND version = :expectedVersion")
    suspend fun updateIfVersionMatches(id: String, expectedVersion: Long, newVersion: Long, domain: String, status: String, state: ByteArray, currentStep: String?, updatedAt: Long): Int
    @androidx.room.Transaction
    suspend fun createWithSnapshot(session: CanvasSessionEntity, snapshot: CanvasSnapshotEntity) {
        insertSession(session)
        insertSnapshot(snapshot)
    }
    @androidx.room.Transaction
    suspend fun updateWithSnapshot(session: CanvasSessionEntity, expectedVersion: Long, snapshot: CanvasSnapshotEntity): Boolean {
        val updated = updateIfVersionMatches(session.id, expectedVersion, session.version, session.domain, session.status, session.encryptedState, session.currentStep, session.updatedAtEpochMillis)
        if (updated != 1) return false
        insertSnapshot(snapshot)
        return true
    }
    @Query("SELECT * FROM canvas_snapshot WHERE canvasId = :canvasId ORDER BY snapshotVersion DESC LIMIT 1") suspend fun latestSnapshot(canvasId: String): CanvasSnapshotEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertStep(step: CanvasStepEntity)
    @Query("SELECT * FROM canvas_step WHERE canvasId = :canvasId ORDER BY stepKey") suspend fun steps(canvasId: String): List<CanvasStepEntity>
}

@Dao interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun append(event: AuditEventEntity)
    @Query("SELECT * FROM audit_event ORDER BY sequence DESC LIMIT :limit") suspend fun latest(limit: Int): List<AuditEventEntity>
    @Query("SELECT * FROM audit_event ORDER BY sequence ASC") suspend fun allAscending(): List<AuditEventEntity>
}

@Dao interface ContextEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun append(event: ContextEventEntity)
    @Query("SELECT * FROM context_event ORDER BY occurredAtEpochMillis DESC LIMIT :limit") suspend fun latest(limit: Int): List<ContextEventEntity>
}


@Dao interface ActionOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun prepare(item: ActionOutboxEntity)
    @Query("SELECT * FROM action_outbox WHERE state IN (:states) ORDER BY updatedAtEpochMillis") suspend fun byStates(states: List<String>): List<ActionOutboxEntity>
    @Query("SELECT * FROM action_outbox WHERE id = :id") suspend fun getById(id: String): ActionOutboxEntity?
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
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCorpusPack(pack: InstalledCorpusPackEntity)
    @Query("SELECT * FROM installed_corpus_pack WHERE packId = :packId AND version = :version LIMIT 1") suspend fun corpusPack(packId: String, version: String): InstalledCorpusPackEntity?
    @Query("SELECT * FROM installed_corpus_pack WHERE status = 'INSTALLED' ORDER BY installedAtEpochMillis DESC") suspend fun corpusPacks(): List<InstalledCorpusPackEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putModel(model: InstalledModelEntity)
    @Query("SELECT * FROM installed_model ORDER BY installedAtEpochMillis DESC") suspend fun models(): List<InstalledModelEntity>
}
