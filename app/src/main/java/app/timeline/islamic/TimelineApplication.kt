package app.timeline.islamic

import android.app.Application
import app.timeline.islamic.core.agent.AuditVerification
import app.timeline.islamic.core.agent.UserConfirmation
import app.timeline.islamic.core.agent.runtime.AgentStepProcessor
import app.timeline.islamic.core.agent.runtime.AgentRunResult
import app.timeline.islamic.core.agent.runtime.AgentRecoveryReport
import app.timeline.islamic.core.agent.runtime.AgentOrchestrator
import app.timeline.islamic.core.agent.runtime.AgentRecoveryCoordinator
import app.timeline.islamic.core.authorization.AuthorizationEngine
import app.timeline.islamic.core.authorization.ApprovedToolHandler
import app.timeline.islamic.core.authorization.ToolExecutionGateway
import app.timeline.islamic.core.persistence.EncryptedDatabaseFactory
import app.timeline.islamic.core.persistence.RoomAgentTaskStore
import app.timeline.islamic.core.persistence.RoomAuditLog
import app.timeline.islamic.core.persistence.RoomAuthorizationStore
import app.timeline.islamic.core.security.KeystoreAuditMacProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TimelineApplication : Application() {
    @Volatile var agentRuntimeReady: Boolean = false
        private set
    @Volatile var agentRuntimeFailureCode: String? = null
        private set

    // Activity recreation must not run recovery a second time while a live process is working.
    private val agentInitializationMutex = Mutex()
    @Volatile private var completedRecoveryReport: AgentRecoveryReport? = null

    // Lazy access keeps UI startup independent from a model; storage errors remain explicit.
    val database by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { EncryptedDatabaseFactory.get(this) }
    private val taskStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomAgentTaskStore(database) }
    private val authorizationStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomAuthorizationStore(database, KeystoreAuditMacProvider(this)) }
    private val authorizationEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AuthorizationEngine(authorizationStore) }
    private val auditLog by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomAuditLog(database, KeystoreAuditMacProvider(this))
    }
    private val agentOrchestrator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AgentOrchestrator(taskStore, authorizationEngine)
    }
    private val agentRecovery by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AgentRecoveryCoordinator(taskStore, authorizationStore)
    }

    /** Must complete before any agent work is scheduled or an action executor is exposed. */
    suspend fun initializeAgentRuntime(): AgentRecoveryReport = agentInitializationMutex.withLock {
        completedRecoveryReport?.let {
            agentRuntimeReady = true
            return@withLock it
        }
        agentRuntimeReady = false
        agentRuntimeFailureCode = null
        try {
            when (val verification = auditLog.verify()) {
                is AuditVerification.Valid -> Unit
                is AuditVerification.Invalid -> error("Audit integrity verification failed: ${verification.reason}")
            }
            agentRecovery.reconcile().also {
                completedRecoveryReport = it
                agentRuntimeReady = true
            }
        } catch (failure: Exception) {
            // Do not log private task/proposal content or silently reset the audit root.
            // A failed attempt is not cached, so a later explicit retry can re-verify/reconcile.
            agentRuntimeFailureCode = failure::class.simpleName ?: "AGENT_RECOVERY_FAILED"
            throw failure
        }
    }

    /** The only app-level entry point for advancing an agent task. Recovery must pass first. */
    suspend fun runAgentStep(taskId: String, workerId: String, processor: AgentStepProcessor): AgentRunResult {
        check(agentRuntimeReady) { "Agent runtime is not initialized; recovery/integrity gate has not passed" }
        return agentOrchestrator.runOneStep(taskId, workerId, processor)
    }

    suspend fun approveAgentProposal(confirmation: UserConfirmation): Boolean {
        check(agentRuntimeReady) { "Agent runtime is not initialized" }
        return authorizationEngine.approve(confirmation)
    }

    suspend fun rejectAgentProposal(proposalId: String, proposalSha256: String): Boolean {
        check(agentRuntimeReady) { "Agent runtime is not initialized" }
        return authorizationEngine.reject(proposalId, proposalSha256)
    }

    suspend fun claimAgentAction(
        proposalId: String, proposalSha256: String, idempotencyKey: String, lease: TaskLease
    ): ExecutionClaimResult {
        check(agentRuntimeReady) { "Agent runtime is not initialized" }
        return authorizationEngine.claimExecution(proposalId, proposalSha256, idempotencyKey, lease)
    }

    fun createToolExecutionGateway(handlers: Map<String, ApprovedToolHandler>): ToolExecutionGateway {
        check(agentRuntimeReady) { "Agent runtime is not initialized" }
        return ToolExecutionGateway(authorizationEngine, handlers)
    }
}
