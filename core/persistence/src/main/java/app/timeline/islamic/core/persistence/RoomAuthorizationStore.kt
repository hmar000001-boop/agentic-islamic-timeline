package app.timeline.islamic.core.persistence

import androidx.room.withTransaction
import app.timeline.islamic.core.agent.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Instant

/**
 * Room adapter for the Authorization Engine. Proposal/approval/grant checks and the transition
 * to EXECUTING are serialized in one SQLite transaction. SQLCipher protects these rows at rest.
 */
class RoomAuthorizationStore(
    private val db: AppDatabase,
    auditMacProvider: AuditMacProvider
) : AuthorizationStore {
    private val auditLog = RoomAuditLog(db, auditMacProvider)
    private val auth get() = db.authorizationDao()
    private val agent get() = db.agentDao()
    private val tasks by lazy { RoomAgentTaskStore(db) }

    override suspend fun persistProposalAndApproval(
        proposal: ActionProposal,
        canonicalParameters: ByteArray,
        approvalId: String,
        lease: TaskLease,
        now: Instant
    ): Boolean = db.withTransaction {
        if (!validProposalBytes(proposal, canonicalParameters, now) || proposal.taskId != lease.taskId) return@withTransaction false
        if (!tasks.hasLiveLease(lease, now.toEpochMilli())) return@withTransaction false
        val task = agent.getTask(proposal.taskId) ?: return@withTransaction false
        if (task.status != AgentTaskStatus.RUNNING.name) return@withTransaction false
        if (agent.transitionTask(task.id, task.status, task.stateVersion, AgentTaskStatus.WAITING_FOR_APPROVAL.name, now.toEpochMilli()) != 1) return@withTransaction false
        auth.insertProposal(proposal.toEntity(canonicalParameters))
        auth.insertApproval(PendingApprovalEntity(
            id = approvalId, proposalId = proposal.proposalId, proposalHash = proposal.parametersSha256,
            state = ApprovalState.PENDING.name, expiresAtEpochMillis = proposal.expiresAt.toEpochMilli(),
            decisionAtEpochMillis = null, sourceGrantId = null
        ))
        check(agent.releaseLease(lease.taskId, lease.owner, lease.fencingToken, now.toEpochMilli()) == 1) {
            "Task lease changed while the approval proposal was being persisted"
        }
        tasks.appendEvent(task.id, "ACTION_PROPOSED_WAITING_APPROVAL", now)
        auditLog.appendWithinTransaction("ACTION_PROPOSED_PENDING", auditPayload(task.id, proposal.proposalId, proposal.actionType, proposal.parametersSha256), now)
        true
    }

    override suspend fun persistProposalUsingHifzGrant(
        proposal: ActionProposal,
        canonicalParameters: ByteArray,
        approvalId: String,
        grantId: String,
        lease: TaskLease,
        now: Instant
    ): Boolean = db.withTransaction {
        if (proposal.actionType != HifzIntervalGrantPolicy.ACTION_TYPE ||
            proposal.impactClass != ImpactClass.LOW || proposal.reversibility != Reversibility.REVERSIBLE) return@withTransaction false
        if (!validProposalBytes(proposal, canonicalParameters, now) || proposal.taskId != lease.taskId) return@withTransaction false
        val change = decodeHifzChange(canonicalParameters) ?: return@withTransaction false
        if (proposal.targetId != change.planId || !tasks.hasLiveLease(lease, now.toEpochMilli())) return@withTransaction false
        val task = agent.getTask(proposal.taskId) ?: return@withTransaction false
        if (task.status != AgentTaskStatus.RUNNING.name) return@withTransaction false
        val grantEntity = auth.grant(grantId) ?: return@withTransaction false
        val grant = grantEntity.toHifzGrant() ?: return@withTransaction false
        if (grantEntity.actionType != HifzIntervalGrantPolicy.ACTION_TYPE ||
            grant.policyVersion != HifzIntervalGrantPolicy.VERSION || !grant.matches(change, now)) return@withTransaction false
        auth.insertProposal(proposal.toEntity(canonicalParameters))
        auth.insertApproval(PendingApprovalEntity(
            id = approvalId, proposalId = proposal.proposalId, proposalHash = proposal.parametersSha256,
            state = ApprovalState.APPROVED.name,
            expiresAtEpochMillis = minOf(proposal.expiresAt.toEpochMilli(), grant.expiresAt.toEpochMilli()),
            decisionAtEpochMillis = now.toEpochMilli(), sourceGrantId = grantId
        ))
        tasks.appendEvent(task.id, "ACTION_PROPOSAL_COVERED_BY_SCOPED_GRANT", now)
        auditLog.appendWithinTransaction("ACTION_PROPOSED_SCOPED", auditPayload(task.id, proposal.proposalId, proposal.actionType, proposal.parametersSha256, grantId), now)
        true
    }

    override suspend fun proposal(proposalId: String): ActionProposal? = auth.proposal(proposalId)?.toDomain()

    override suspend fun approveExact(confirmation: UserConfirmation, approvalExpiresAt: Instant): Boolean = db.withTransaction {
        val row = auth.proposal(confirmation.proposalId) ?: return@withTransaction false
        val confirmedAt = confirmation.confirmedAt.toEpochMilli()
        if (row.parametersSha256 != confirmation.proposalSha256 ||
            confirmedAt < row.createdAtEpochMillis || confirmedAt >= row.expiresAtEpochMillis ||
            approvalExpiresAt.toEpochMilli() <= confirmedAt ||
            approvalExpiresAt.isAfter(confirmation.confirmedAt.plusMillis(AuthorizationPolicyLimits.MAX_APPROVAL_LIFETIME_MILLIS))) return@withTransaction false
        val requiresStrong = ImpactClass.valueOf(row.impactClass) in setOf(
            ImpactClass.HIGH, ImpactClass.SENSITIVE, ImpactClass.EXTERNAL, ImpactClass.FINANCIAL,
            ImpactClass.DESTRUCTIVE, ImpactClass.IRREVERSIBLE
        ) || Reversibility.valueOf(row.reversibility) in setOf(Reversibility.IRREVERSIBLE, Reversibility.UNKNOWN)
        if (requiresStrong && confirmation.strength != ConfirmationStrength.STRONG) return@withTransaction false
        val approval = auth.approvals(row.id).firstOrNull {
            it.proposalHash == confirmation.proposalSha256 && it.state == ApprovalState.PENDING.name
        } ?: return@withTransaction false
        val effectiveExpiry = minOf(approvalExpiresAt.toEpochMilli(), row.expiresAtEpochMillis)
        if (effectiveExpiry <= confirmation.confirmedAt.toEpochMilli()) return@withTransaction false
        val task = agent.getTask(row.taskId) ?: return@withTransaction false
        if (task.status != AgentTaskStatus.WAITING_FOR_APPROVAL.name) return@withTransaction false
        if (agent.transitionTask(task.id, task.status, task.stateVersion, AgentTaskStatus.RUNNABLE.name, confirmation.confirmedAt.toEpochMilli()) != 1) return@withTransaction false
        check(auth.decideApproval(
                approval.id, confirmation.proposalSha256, ApprovalState.PENDING.name,
                ApprovalState.APPROVED.name, confirmation.confirmedAt.toEpochMilli(), effectiveExpiry
            ) == 1) { "Approval changed during the transaction" }
        agent.expireLeaseForTask(task.id, confirmation.confirmedAt.toEpochMilli())
        tasks.appendEvent(task.id, "ACTION_APPROVED_REQUEUE", confirmation.confirmedAt)
        auditLog.appendWithinTransaction("ACTION_APPROVED", auditPayload(task.id, row.id, row.parametersSha256), confirmation.confirmedAt)
        true
    }

    override suspend fun rejectExact(proposalId: String, proposalSha256: String, at: Instant): Boolean = db.withTransaction {
        val row = auth.proposal(proposalId) ?: return@withTransaction false
        if (row.parametersSha256 != proposalSha256 || row.expiresAtEpochMillis <= at.toEpochMilli()) return@withTransaction false
        val approval = auth.approvals(proposalId).firstOrNull {
            it.proposalHash == proposalSha256 && it.state == ApprovalState.PENDING.name
        } ?: return@withTransaction false
        val task = agent.getTask(row.taskId) ?: return@withTransaction false
        if (task.status != AgentTaskStatus.WAITING_FOR_APPROVAL.name) return@withTransaction false
        if (agent.transitionTask(task.id, task.status, task.stateVersion, AgentTaskStatus.CANCELLED.name, at.toEpochMilli()) != 1) return@withTransaction false
        check(auth.decideApproval(approval.id, proposalSha256, ApprovalState.PENDING.name, ApprovalState.REJECTED.name, at.toEpochMilli(), at.toEpochMilli()) == 1) {
            "Approval changed during rejection transaction"
        }
        agent.expireLeaseForTask(task.id, at.toEpochMilli())
        tasks.appendEvent(task.id, "ACTION_REJECTED_TASK_CANCELLED", at)
        auditLog.appendWithinTransaction("ACTION_REJECTED", auditPayload(task.id, row.id, row.parametersSha256), at)
        true
    }

    override suspend fun createHifzGrant(grant: HifzIntervalGrant): Boolean = db.withTransaction {
        require(grant.maxAbsoluteDeltaMinutes in 1..HifzIntervalGrantPolicy.MAX_ABSOLUTE_DELTA_MINUTES)
        require(grant.revokedAt == null)
        require(grant.policyVersion == HifzIntervalGrantPolicy.VERSION)
        require(!grant.expiresAt.isAfter(grant.createdAt.plusMillis(HifzIntervalGrantPolicy.MAX_LIFETIME_MILLIS)))
        auth.insertGrant(PermissionGrantEntity(
            id = grant.grantId, actionType = HifzIntervalGrantPolicy.ACTION_TYPE,
            scopeEncrypted = grant.planId.toByteArray(Charsets.UTF_8),
            constraintsEncrypted = encodeGrantConstraints(grant),
            createdAtEpochMillis = grant.createdAt.toEpochMilli(), expiresAtEpochMillis = grant.expiresAt.toEpochMilli(),
            revokedAtEpochMillis = grant.revokedAt?.toEpochMilli(), policyVersion = grant.policyVersion
        ))
        auditLog.appendWithinTransaction("HIFZ_SCOPED_GRANT_CREATED", auditPayload(grant.grantId, grant.planId, grant.immutableParametersSha256, grant.maxAbsoluteDeltaMinutes.toString(), grant.policyVersion), grant.createdAt)
        true
    }

    override suspend fun revokeGrant(grantId: String, at: Instant): Boolean = db.withTransaction {
        val changed = auth.revokeGrant(grantId, at.toEpochMilli())
        if (changed != 1) return@withTransaction false
        auditLog.appendWithinTransaction("PERMISSION_GRANT_REVOKED", auditPayload(grantId), at)
        true
    }

    override suspend fun matchingHifzGrant(change: HifzIntervalChange, now: Instant): HifzIntervalGrant? =
        auth.grantsForAction(HifzIntervalGrantPolicy.ACTION_TYPE).asSequence()
            .mapNotNull { it.toHifzGrant() }
            .firstOrNull { it.matches(change, now) }

    override suspend fun beginClaimedExecution(claim: ExecutionClaim, now: Instant): Boolean = db.withTransaction {
        val row = auth.execution(claim.executionId) ?: return@withTransaction false
        val outbox = db.actionOutboxDao().getById(claim.executionId) ?: return@withTransaction false
        if (row.state != "CLAIMED" || outbox.state != "CLAIMED") return@withTransaction false
        if (row.proposalId != claim.proposalId || outbox.proposalId != claim.proposalId ||
            row.proposalHash != claim.proposalSha256 || outbox.actionHash != claim.proposalSha256 ||
            row.idempotencyKey != claim.idempotencyKey || outbox.idempotencyKey != claim.idempotencyKey) return@withTransaction false
        // The claim timestamp is historical. Every expiry and lease is rechecked at actual
        // executor start, not at the earlier moment when the claim was created.
        val nowMs = now.toEpochMilli()
        val proposal = auth.proposal(claim.proposalId) ?: return@withTransaction false
        if (proposal.parametersSha256 != claim.proposalSha256 || proposal.taskId != claim.taskId ||
            proposal.actionType != claim.actionType || proposal.targetId != claim.targetId ||
            proposal.expiresAtEpochMillis <= nowMs) return@withTransaction false
        val approval = auth.approvals(claim.proposalId).firstOrNull { it.proposalHash == claim.proposalSha256 } ?: return@withTransaction false
        if (approval.state != ApprovalState.APPROVED.name || approval.expiresAtEpochMillis <= nowMs) return@withTransaction false
        if (approval.sourceGrantId != null) {
            val grantEntity = auth.grant(approval.sourceGrantId) ?: return@withTransaction false
            val grant = grantEntity.toHifzGrant() ?: return@withTransaction false
            val change = decodeHifzChange(proposal.canonicalParametersEncrypted) ?: return@withTransaction false
            if (grantEntity.actionType != HifzIntervalGrantPolicy.ACTION_TYPE ||
                grant.policyVersion != HifzIntervalGrantPolicy.VERSION || !grant.matches(change, now)) return@withTransaction false
        }
        val task = agent.getTask(claim.taskId) ?: return@withTransaction false
        if (task.status != AgentTaskStatus.RUNNING.name) return@withTransaction false
        val leaseRow = agent.lease(claim.taskId) ?: return@withTransaction false
        if (leaseRow.leaseOwner != claim.leaseOwner || leaseRow.fencingToken != claim.fencingToken ||
            leaseRow.acquiredAtEpochMillis > nowMs || leaseRow.heartbeatAtEpochMillis > nowMs ||
            leaseRow.expiresAtEpochMillis <= nowMs) return@withTransaction false
        if (auth.startExecution(claim.executionId, claim.proposalSha256, claim.idempotencyKey, nowMs) != 1) return@withTransaction false
        check(auth.startOutbox(claim.executionId, claim.proposalSha256, claim.idempotencyKey, nowMs) == 1) {
            "Outbox claim changed while starting the execution"
        }
        tasks.appendEvent(task.id, "ACTION_EXECUTOR_STARTED", now)
        auditLog.appendWithinTransaction("ACTION_EXECUTION_STARTED", auditPayload(claim.taskId, claim.proposalId, claim.proposalSha256, claim.executionId, claim.idempotencyKey), now)
        true
    }

    override suspend fun claimApprovedExecution(
        proposalId: String,
        expectedProposalSha256: String,
        idempotencyKey: String,
        lease: TaskLease,
        now: Instant
    ): ExecutionClaimResult = db.withTransaction {
        if (idempotencyKey.isBlank() || idempotencyKey.length > 200) return@withTransaction ExecutionClaimResult.LeaseMismatch
        val proposal = auth.proposal(proposalId) ?: return@withTransaction ExecutionClaimResult.NotFound
        if (proposal.parametersSha256 != expectedProposalSha256) return@withTransaction ExecutionClaimResult.HashMismatch
        if (proposal.expiresAtEpochMillis <= now.toEpochMilli()) return@withTransaction ExecutionClaimResult.ProposalExpired
        if (proposal.taskId != lease.taskId) return@withTransaction ExecutionClaimResult.LeaseMismatch
        val task = agent.getTask(proposal.taskId) ?: return@withTransaction ExecutionClaimResult.NotFound
        if (task.status != AgentTaskStatus.RUNNING.name) return@withTransaction ExecutionClaimResult.TaskNotRunning
        if (!tasks.hasLiveLease(lease, now.toEpochMilli())) return@withTransaction ExecutionClaimResult.LeaseMismatch
        val approval = auth.approvals(proposalId).firstOrNull { it.proposalHash == expectedProposalSha256 }
            ?: return@withTransaction ExecutionClaimResult.ApprovalMissing
        if (approval.state != ApprovalState.APPROVED.name) return@withTransaction ExecutionClaimResult.ApprovalMissing
        if (approval.expiresAtEpochMillis <= now.toEpochMilli()) return@withTransaction ExecutionClaimResult.ApprovalExpired
        if (approval.sourceGrantId != null) {
            val grantEntity = auth.grant(approval.sourceGrantId) ?: return@withTransaction ExecutionClaimResult.ApprovalMissing
            val grant = grantEntity.toHifzGrant() ?: return@withTransaction ExecutionClaimResult.ApprovalMissing
            val change = decodeHifzChange(proposal.canonicalParametersEncrypted) ?: return@withTransaction ExecutionClaimResult.ApprovalMissing
            if (grantEntity.actionType != HifzIntervalGrantPolicy.ACTION_TYPE ||
                grant.policyVersion != HifzIntervalGrantPolicy.VERSION || !grant.matches(change, now)) {
                return@withTransaction ExecutionClaimResult.ApprovalMissing
            }
        }
        if (auth.executionForProposal(proposalId) != null) return@withTransaction ExecutionClaimResult.AlreadyClaimed
        val executionId = java.util.UUID.randomUUID().toString()
        auth.insertExecution(ActionExecutionEntity(
            id = executionId, proposalId = proposalId, proposalHash = expectedProposalSha256, idempotencyKey = idempotencyKey,
            state = "CLAIMED", encryptedReceipt = null, undoState = "NOT_AVAILABLE",
            createdAtEpochMillis = now.toEpochMilli(), updatedAtEpochMillis = now.toEpochMilli()
        ))
        auth.insertOutbox(ActionOutboxEntity(
            id = executionId, proposalId = proposalId, actionHash = expectedProposalSha256,
            idempotencyKey = idempotencyKey, state = "CLAIMED", attempt = 0,
            externalRef = null, encryptedReceipt = null, updatedAtEpochMillis = now.toEpochMilli()
        ))
        tasks.appendEvent(task.id, "ACTION_EXECUTION_CLAIMED", now)
        auditLog.appendWithinTransaction("ACTION_EXECUTION_CLAIMED", auditPayload(task.id, proposalId, expectedProposalSha256, executionId), now)
        ExecutionClaimResult.Claimed(ExecutionClaim(
            executionId, proposalId, expectedProposalSha256, proposal.actionType, proposal.targetId,
            idempotencyKey, proposal.taskId, lease.owner, lease.fencingToken, now
        ))
    }

    override suspend fun markExecutionOutcome(claim: ExecutionClaim, terminalState: String, receipt: ByteArray?, now: Instant): Boolean = db.withTransaction {
        require(terminalState in setOf("VERIFIED", "FAILED", "OUTCOME_UNKNOWN", "APPLIED_UNVERIFIED"))
        if (receipt != null && receipt.size > 64 * 1024) return@withTransaction false
        val execution = auth.execution(claim.executionId) ?: return@withTransaction false
        if (execution.state != "EXECUTING" || execution.proposalHash != claim.proposalSha256 ||
            execution.idempotencyKey != claim.idempotencyKey || claim.claimedAt.isAfter(now)) return@withTransaction false
        val outbox = checkNotNull(db.actionOutboxDao().getById(claim.executionId)) { "Execution outbox is missing" }
        if (outbox.state != "EXECUTING" || outbox.proposalId != claim.proposalId ||
            outbox.actionHash != claim.proposalSha256 || outbox.idempotencyKey != claim.idempotencyKey) return@withTransaction false
        val proposal = checkNotNull(auth.proposal(outbox.proposalId)) { "Execution proposal is missing" }
        if (proposal.id != claim.proposalId || proposal.taskId != claim.taskId ||
            proposal.actionType != claim.actionType || proposal.targetId != claim.targetId ||
            proposal.parametersSha256 != claim.proposalSha256) return@withTransaction false
        val task = checkNotNull(agent.getTask(proposal.taskId)) { "Execution task is missing" }
        if (auth.finishExecution(claim.executionId, terminalState, receipt, now.toEpochMilli()) != 1) return@withTransaction false
        check(auth.finishOutbox(claim.executionId, terminalState, receipt, now.toEpochMilli()) == 1) {
            "Outbox/execution state diverged; transaction rolled back"
        }
        auditLog.appendWithinTransaction("ACTION_EXECUTION_OUTCOME", auditPayload(claim.executionId, terminalState, execution.proposalHash), now)

        val lease = agent.lease(claim.taskId)
        // Ownership is established by the monotonic fence, not merely by a live timestamp.
        // If that exact fence has expired but no newer worker has acquired the task, the
        // completed result may safely requeue/interupt it; a newer fence always wins.
        val ownsFence = lease != null && lease.leaseOwner == claim.leaseOwner &&
            lease.fencingToken == claim.fencingToken && lease.acquiredAtEpochMillis <= now.toEpochMilli() &&
            lease.heartbeatAtEpochMillis <= now.toEpochMilli()
        if (task.status == AgentTaskStatus.RUNNING.name && ownsFence) {
            val nextStatus = if (terminalState == "OUTCOME_UNKNOWN" || terminalState == "APPLIED_UNVERIFIED") {
                AgentTaskStatus.INTERRUPTED
            } else {
                // A known outcome returns the task to the queue so the next bounded planning
                // step can consume the persisted execution result. A stale worker cannot do this.
                AgentTaskStatus.RUNNABLE
            }
            check(agent.transitionTask(task.id, task.status, task.stateVersion, nextStatus.name, now.toEpochMilli()) == 1) {
                "Task state changed while recording execution outcome"
            }
            val ownedLease = requireNotNull(lease) { "Lease disappeared after fence ownership check" }
            val released = if (ownedLease.expiresAtEpochMillis > now.toEpochMilli()) {
                agent.releaseLease(task.id, claim.leaseOwner, claim.fencingToken, now.toEpochMilli())
            } else {
                agent.expireLeaseForTask(task.id, now.toEpochMilli())
            }
            check(released == 1) { "Execution fence changed while recording the result" }
            tasks.appendEvent(task.id, if (nextStatus == AgentTaskStatus.INTERRUPTED) "RECONCILIATION_REQUIRED" else "ACTION_OUTCOME_RECORDED_REQUEUED", now)
        } else {
            // The result is retained, but a worker that lost its fencing capability cannot
            // change the task's state or release a lease acquired by a newer worker.
            tasks.appendEvent(task.id, "ACTION_OUTCOME_RECORDED_AFTER_LEASE_LOST", now)
        }
        true
    }

    override suspend fun reconcileExecutingExecutions(now: Instant): Int = db.withTransaction {
        var count = 0
        // CLAIMED means no external handler was started; this can be safely aborted.
        val claimed = auth.executionsByState("CLAIMED")
        claimed.forEach { execution ->
            val outbox = checkNotNull(db.actionOutboxDao().getById(execution.id)) { "Claimed execution outbox is missing" }
            val proposal = checkNotNull(auth.proposal(outbox.proposalId)) { "Claimed execution proposal is missing" }
            check(auth.finishExecution(execution.id, "ABORTED_BEFORE_EXECUTION", null, now.toEpochMilli()) == 1)
            check(auth.finishOutbox(execution.id, "ABORTED_BEFORE_EXECUTION", null, now.toEpochMilli()) == 1)
            val task = agent.getTask(proposal.taskId)
            if (task?.status == AgentTaskStatus.RUNNING.name) {
                check(agent.transitionTask(task.id, task.status, task.stateVersion, AgentTaskStatus.INTERRUPTED.name, now.toEpochMilli()) == 1)
                agent.expireLeaseForTask(task.id, now.toEpochMilli())
            }
            if (task != null) tasks.appendEvent(task.id, "CLAIM_ABORTED_BEFORE_EXTERNAL_EXECUTION", now)
            auditLog.appendWithinTransaction("EXECUTION_CLAIM_ABORTED", auditPayload(execution.id, execution.proposalHash), now)
            count++
        }
        // EXECUTING means the process may have performed the external effect. Never replay it.
        val active = auth.executionsByState("EXECUTING")
        active.forEach { execution ->
            val outbox = checkNotNull(db.actionOutboxDao().getById(execution.id)) { "Executing outbox is missing" }
            val proposal = checkNotNull(auth.proposal(outbox.proposalId)) { "Executing proposal is missing" }
            check(auth.finishExecution(execution.id, "OUTCOME_UNKNOWN", execution.encryptedReceipt, now.toEpochMilli()) == 1)
            check(auth.finishOutbox(execution.id, "OUTCOME_UNKNOWN", execution.encryptedReceipt, now.toEpochMilli()) == 1)
            val task = agent.getTask(proposal.taskId)
            if (task?.status == AgentTaskStatus.RUNNING.name) {
                check(agent.transitionTask(task.id, task.status, task.stateVersion, AgentTaskStatus.INTERRUPTED.name, now.toEpochMilli()) == 1)
                agent.expireLeaseForTask(task.id, now.toEpochMilli())
            }
            if (task != null) tasks.appendEvent(task.id, "RECONCILIATION_REQUIRED_AFTER_PROCESS_RESTART", now)
            auditLog.appendWithinTransaction("EXECUTION_OUTCOME_UNKNOWN_AFTER_RESTART", auditPayload(execution.id, execution.proposalHash), now)
            count++
        }
        count
    }

    override suspend fun expirePendingApprovals(now: Instant): Int = db.withTransaction {
        val expired = auth.expiredPendingApprovals(now.toEpochMilli())
        var count = 0
        expired.forEach { approval ->
            if (auth.expireApproval(approval.id, now.toEpochMilli()) != 1) return@forEach
            val proposal = checkNotNull(auth.proposal(approval.proposalId)) { "Approval proposal is missing" }
            val task = checkNotNull(agent.getTask(proposal.taskId)) { "Approval task is missing" }
            if (task.status == AgentTaskStatus.WAITING_FOR_APPROVAL.name) {
                check(agent.transitionTask(task.id, task.status, task.stateVersion, AgentTaskStatus.RUNNABLE.name, now.toEpochMilli()) == 1)
                agent.expireLeaseForTask(task.id, now.toEpochMilli())
                tasks.appendEvent(task.id, "APPROVAL_EXPIRED_TASK_REQUEUED", now)
            }
            auditLog.appendWithinTransaction("APPROVAL_EXPIRED", auditPayload(approval.id, approval.proposalId, approval.proposalHash), now)
            count++
        }
        count
    }

    override suspend fun approvals(proposalId: String): List<ApprovalSnapshot> = auth.approvals(proposalId).map {
        ApprovalSnapshot(it.id, it.proposalId, it.proposalHash, ApprovalState.valueOf(it.state),
            Instant.ofEpochMilli(it.expiresAtEpochMillis), it.decisionAtEpochMillis?.let(Instant::ofEpochMilli), it.sourceGrantId)
    }

    private fun auditPayload(vararg fields: String): ByteArray = fields.joinToString("\u001f").toByteArray(Charsets.UTF_8)

    private fun validProposalBytes(proposal: ActionProposal, bytes: ByteArray, now: Instant): Boolean =
        bytes.isNotEmpty() && bytes.size <= 64 * 1024 && proposal.createdAt == now &&
            proposal.expiresAt.isAfter(now) && sha256(bytes) == proposal.parametersSha256

    private fun ActionProposal.toEntity(bytes: ByteArray) = ActionProposalEntity(
        id = proposalId, taskId = taskId, actionType = actionType, targetId = targetId,
        canonicalParametersEncrypted = bytes.copyOf(), parametersSha256 = parametersSha256,
        createdAtEpochMillis = createdAt.toEpochMilli(), expiresAtEpochMillis = expiresAt.toEpochMilli(),
        impactClass = impactClass.name, reversibility = reversibility.name
    )

    private fun ActionProposalEntity.toDomain() = ActionProposal(
        id, taskId, actionType, targetId, parametersSha256,
        Instant.ofEpochMilli(createdAtEpochMillis), Instant.ofEpochMilli(expiresAtEpochMillis),
        ImpactClass.valueOf(impactClass), Reversibility.valueOf(reversibility)
    )

    private fun PermissionGrantEntity.toHifzGrant(): HifzIntervalGrant? = try {
        if (actionType != HifzIntervalGrantPolicy.ACTION_TYPE) return null
        val planId = scopeEncrypted.toString(Charsets.UTF_8)
        val constraints = DataInputStream(ByteArrayInputStream(constraintsEncrypted)).use { input ->
            require(input.readInt() == GRANT_CODEC_VERSION)
            val fingerprint = input.readUTF()
            val maxDelta = input.readInt()
            require(input.available() == 0)
            fingerprint to maxDelta
        }
        HifzIntervalGrant(
            grantId = id, planId = planId, immutableParametersSha256 = constraints.first,
            maxAbsoluteDeltaMinutes = constraints.second,
            createdAt = Instant.ofEpochMilli(createdAtEpochMillis), expiresAt = Instant.ofEpochMilli(expiresAtEpochMillis),
            revokedAt = revokedAtEpochMillis?.let(Instant::ofEpochMilli), policyVersion = policyVersion
        )
    } catch (_: Exception) { null }

    private fun encodeGrantConstraints(grant: HifzIntervalGrant): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(GRANT_CODEC_VERSION)
            out.writeUTF(grant.immutableParametersSha256)
            out.writeInt(grant.maxAbsoluteDeltaMinutes)
        }
        bytes.toByteArray()
    }

    private fun decodeHifzChange(bytes: ByteArray): HifzIntervalChange? = try {
        val values = bytes.toString(Charsets.UTF_8).split('\u001f')
        require(values.size == 5 && values[0] == "hifz.interval.v1")
        HifzIntervalChange(values[1], values[2].toLong(), values[3].toInt(), values[4])
    } catch (_: Exception) { null }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object { const val GRANT_CODEC_VERSION = 1 }
}
