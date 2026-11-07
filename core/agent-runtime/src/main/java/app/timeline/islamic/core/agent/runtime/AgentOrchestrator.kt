package app.timeline.islamic.core.agent.runtime

import app.timeline.islamic.core.agent.*
import app.timeline.islamic.core.common.Clock
import app.timeline.islamic.core.common.SystemClock
import java.security.MessageDigest
import java.time.Instant

/** Data supplied by the local model/domain planner. It is never an executable tool call. */
sealed interface AgentStepOutcome {
    data class Continue(val checkpoint: CheckpointDraft, val nextEligibleAt: Instant? = null) : AgentStepOutcome
    data class AwaitInput(val checkpoint: CheckpointDraft, val promptKey: String) : AgentStepOutcome
    data class Complete(val checkpoint: CheckpointDraft) : AgentStepOutcome
    data class Fail(val checkpoint: CheckpointDraft?, val reasonCode: String) : AgentStepOutcome
    data class ProposeAction(val checkpoint: CheckpointDraft, val draft: ActionDraft) : AgentStepOutcome
    data class ProposeHifzInterval(val checkpoint: CheckpointDraft, val change: HifzIntervalChange) : AgentStepOutcome
}

data class CheckpointDraft(
    val stateSchemaVersion: Int,
    val currentStepId: String?,
    val encryptedPayload: ByteArray
) {
    init {
        require(stateSchemaVersion > 0)
        require(encryptedPayload.isNotEmpty() && encryptedPayload.size <= 512 * 1024)
    }
}

data class ActionDraft(
    val actionType: String,
    val targetId: String?,
    val canonicalParameters: ByteArray,
    val impactClass: ImpactClass,
    val reversibility: Reversibility,
    val proposalLifetimeMillis: Long = 10 * 60 * 1000L
) {
    init {
        require(actionType.isNotBlank())
        require(canonicalParameters.isNotEmpty() && canonicalParameters.size <= 64 * 1024)
        require(proposalLifetimeMillis in 1_000..24 * 60 * 60 * 1000L)
    }
}

fun interface AgentStepProcessor {
    suspend fun process(task: AgentTaskSnapshot, checkpoint: TaskCheckpoint?): AgentStepOutcome
}

sealed interface AgentRunResult {
    data class Yielded(val taskId: String, val status: AgentTaskStatus) : AgentRunResult
    data class AwaitingApproval(val proposal: ActionProposal, val approvalId: String) : AgentRunResult
    data class ScopedActionReady(val proposal: ActionProposal, val grantId: String, val lease: TaskLease) : AgentRunResult
    data class AwaitingInput(val taskId: String, val promptKey: String) : AgentRunResult
    data class Failed(val taskId: String, val reasonCode: String) : AgentRunResult
    data object NotRunnable : AgentRunResult
}

/** Executes exactly one bounded planning step. Long-running loops are scheduled by the host. */
class AgentOrchestrator(
    private val tasks: AgentTaskStore,
    private val authorization: AuthorizationServicePort,
    private val clock: Clock = SystemClock
) {
    suspend fun runOneStep(
        taskId: String,
        workerId: String,
        processor: AgentStepProcessor,
        leaseTtlMillis: Long = 60_000L
    ): AgentRunResult {
        val acquiredAt = clock.now()
        val lease = when (val result = tasks.acquireLease(taskId, workerId, acquiredAt, leaseTtlMillis)) {
            is LeaseAcquireResult.Acquired -> result.lease
            else -> return AgentRunResult.NotRunnable
        }
        val task = tasks.getTask(taskId) ?: run {
            tasks.releaseLease(lease, clock.now())
            return AgentRunResult.NotRunnable
        }
        val checkpoint = tasks.latestCheckpoint(taskId)
        val outcome = try {
            processor.process(task, checkpoint)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            // Leave the lease to expire; startup reconciliation moves RUNNING to INTERRUPTED.
            throw cancelled
        } catch (error: Exception) {
            tasks.transition(lease, AgentTaskStatus.FAILED, clock.now(), "TASK_STEP_FAILED")
            return AgentRunResult.Failed(taskId, "STEP_PROCESSOR_FAILURE")
        }

        val draft = when (outcome) {
            is AgentStepOutcome.Continue -> outcome.checkpoint
            is AgentStepOutcome.AwaitInput -> outcome.checkpoint
            is AgentStepOutcome.Complete -> outcome.checkpoint
            is AgentStepOutcome.Fail -> outcome.checkpoint
            is AgentStepOutcome.ProposeAction -> outcome.checkpoint
            is AgentStepOutcome.ProposeHifzInterval -> outcome.checkpoint
        }
        if (draft != null && !persistCheckpoint(lease, checkpoint, draft)) {
            tasks.releaseLease(lease, clock.now())
            return AgentRunResult.Failed(taskId, "CHECKPOINT_REJECTED")
        }

        return when (outcome) {
            is AgentStepOutcome.Continue -> {
                val target = AgentTaskStatus.RUNNABLE
                if (tasks.transition(lease, target, clock.now(), "TASK_STEP_YIELDED")) AgentRunResult.Yielded(taskId, target)
                else AgentRunResult.Failed(taskId, "TASK_TRANSITION_REJECTED")
            }
            is AgentStepOutcome.AwaitInput -> {
                if (tasks.transition(lease, AgentTaskStatus.WAITING_FOR_INPUT, clock.now(), "TASK_WAITING_FOR_INPUT")) {
                    AgentRunResult.AwaitingInput(taskId, outcome.promptKey)
                } else AgentRunResult.Failed(taskId, "TASK_TRANSITION_REJECTED")
            }
            is AgentStepOutcome.Complete -> {
                if (tasks.transition(lease, AgentTaskStatus.COMPLETED, clock.now(), "TASK_COMPLETED")) {
                    AgentRunResult.Yielded(taskId, AgentTaskStatus.COMPLETED)
                } else AgentRunResult.Failed(taskId, "TASK_TRANSITION_REJECTED")
            }
            is AgentStepOutcome.Fail -> {
                tasks.transition(lease, AgentTaskStatus.FAILED, clock.now(), "TASK_FAILED:${outcome.reasonCode}")
                AgentRunResult.Failed(taskId, outcome.reasonCode)
            }
            is AgentStepOutcome.ProposeAction -> when (val result = authorization.propose(
                taskId = taskId, lease = lease, actionType = outcome.draft.actionType,
                targetId = outcome.draft.targetId, canonicalParameters = outcome.draft.canonicalParameters,
                impactClass = outcome.draft.impactClass, reversibility = outcome.draft.reversibility,
                expiresAt = clock.now().plusMillis(outcome.draft.proposalLifetimeMillis)
            )) {
                is ActionProposalResult.NeedsApproval -> AgentRunResult.AwaitingApproval(result.proposal, result.approvalId)
                is ActionProposalResult.CoveredByScopedGrant -> AgentRunResult.Failed(taskId, "UNEXPECTED_GRANT_FOR_GENERIC_ACTION")
                ActionProposalResult.Unavailable -> {
                    tasks.releaseLease(lease, clock.now())
                    AgentRunResult.Failed(taskId, "PROPOSAL_PERSISTENCE_FAILED")
                }
            }
            is AgentStepOutcome.ProposeHifzInterval -> when (val result = authorization.proposeHifzIntervalChange(
                taskId, lease, outcome.change, clock.now().plusSeconds(10 * 60)
            )) {
                is ActionProposalResult.NeedsApproval -> AgentRunResult.AwaitingApproval(result.proposal, result.approvalId)
                is ActionProposalResult.CoveredByScopedGrant -> AgentRunResult.ScopedActionReady(result.proposal, result.grantId, lease)
                ActionProposalResult.Unavailable -> {
                    tasks.releaseLease(lease, clock.now())
                    AgentRunResult.Failed(taskId, "PROPOSAL_PERSISTENCE_FAILED")
                }
            }
        }
    }

    private suspend fun persistCheckpoint(
        lease: TaskLease,
        latest: TaskCheckpoint?,
        draft: CheckpointDraft
    ): Boolean {
        val now = clock.now()
        val payload = draft.encryptedPayload.copyOf()
        return tasks.appendCheckpoint(lease, TaskCheckpoint(
            taskId = lease.taskId, version = (latest?.version ?: 0L) + 1L,
            stateSchemaVersion = draft.stateSchemaVersion, currentStepId = draft.currentStepId,
            encryptedPayload = payload, payloadSha256 = sha256(payload), createdAt = now
        ))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}

data class AgentRecoveryReport(
    val expiredLeasesInterrupted: Int,
    val pendingApprovalsExpired: Int,
    val executionRecordsReconciled: Int
)

/** Run once on process startup before scheduling or resuming any agent work. */
class AgentRecoveryCoordinator(
    private val tasks: AgentTaskStore,
    private val authorizationStore: AuthorizationStore,
    private val clock: Clock = SystemClock
) {
    suspend fun reconcile(): AgentRecoveryReport {
        val now = clock.now()
        val interrupted = tasks.reconcileExpiredLeases(now)
        val expired = authorizationStore.expirePendingApprovals(now)
        val executions = authorizationStore.reconcileExecutingExecutions(now)
        return AgentRecoveryReport(interrupted, expired, executions)
    }
}
