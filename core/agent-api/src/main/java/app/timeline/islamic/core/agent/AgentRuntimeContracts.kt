package app.timeline.islamic.core.agent

import java.time.Instant

/** A lease is a fencing capability. Its token must increase monotonically for every reacquisition. */
data class TaskLease(
    val taskId: String,
    val owner: String,
    val acquiredAt: Instant,
    val expiresAt: Instant,
    val fencingToken: Long
) {
    init {
        require(taskId.isNotBlank() && owner.isNotBlank())
        require(expiresAt.isAfter(acquiredAt))
        require(fencingToken > 0)
    }
}

data class TaskCheckpoint(
    val taskId: String,
    val version: Long,
    val stateSchemaVersion: Int,
    val currentStepId: String?,
    val encryptedPayload: ByteArray,
    val payloadSha256: String,
    val createdAt: Instant
) {
    init {
        require(taskId.isNotBlank())
        require(version > 0 && stateSchemaVersion > 0)
        require(payloadSha256.matches(Regex("[0-9a-f]{64}")))
    }
}

sealed interface LeaseAcquireResult {
    data class Acquired(val lease: TaskLease) : LeaseAcquireResult
    data object TaskUnavailable : LeaseAcquireResult
    data object LeaseHeld : LeaseAcquireResult
    data object TaskNotRunnable : LeaseAcquireResult
}

/** Port implemented by Room. Every mutating method validates the lease in the same transaction. */
interface AgentTaskStore {
    suspend fun createTask(taskId: String, goalType: String, now: Instant)
    suspend fun getTask(taskId: String): AgentTaskSnapshot?
    suspend fun latestCheckpoint(taskId: String): TaskCheckpoint?
    suspend fun reconcileExpiredLeases(now: Instant): Int
    suspend fun resumeInterrupted(taskId: String, expectedVersion: Long, now: Instant): Boolean
    suspend fun resumeAfterInput(taskId: String, expectedVersion: Long, encryptedInput: ByteArray, inputType: String, now: Instant): Boolean
    suspend fun acquireLease(taskId: String, owner: String, now: Instant, ttlMillis: Long): LeaseAcquireResult
    suspend fun heartbeat(lease: TaskLease, now: Instant, ttlMillis: Long): Boolean
    suspend fun appendCheckpoint(lease: TaskLease, checkpoint: TaskCheckpoint): Boolean
    suspend fun transition(lease: TaskLease, target: AgentTaskStatus, now: Instant, eventType: String): Boolean
    suspend fun releaseLease(lease: TaskLease, now: Instant): Boolean
    suspend fun events(taskId: String): List<AgentTaskEventSnapshot>
}

data class AgentTaskSnapshot(
    val id: String,
    val goalType: String,
    val status: AgentTaskStatus,
    val stateVersion: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val nextEligibleAt: Instant?
)

data class AgentTaskEventSnapshot(
    val eventId: String,
    val taskId: String,
    val sequence: Long,
    val eventType: String,
    val occurredAt: Instant
)

object AgentTaskStateMachine {
    private val allowed = mapOf(
        AgentTaskStatus.RUNNABLE to setOf(AgentTaskStatus.RUNNING, AgentTaskStatus.CANCELLED),
        AgentTaskStatus.RUNNING to setOf(AgentTaskStatus.RUNNABLE, AgentTaskStatus.WAITING_FOR_INPUT,
            AgentTaskStatus.WAITING_FOR_APPROVAL, AgentTaskStatus.INTERRUPTED,
            AgentTaskStatus.COMPLETED, AgentTaskStatus.FAILED, AgentTaskStatus.CANCELLED),
        AgentTaskStatus.WAITING_FOR_INPUT to setOf(AgentTaskStatus.RUNNABLE, AgentTaskStatus.CANCELLED),
        AgentTaskStatus.WAITING_FOR_APPROVAL to setOf(AgentTaskStatus.RUNNABLE, AgentTaskStatus.CANCELLED),
        AgentTaskStatus.INTERRUPTED to setOf(AgentTaskStatus.RUNNABLE, AgentTaskStatus.CANCELLED),
        AgentTaskStatus.COMPLETED to emptySet(),
        AgentTaskStatus.FAILED to setOf(AgentTaskStatus.RUNNABLE),
        AgentTaskStatus.CANCELLED to emptySet()
    )

    fun canTransition(from: AgentTaskStatus, to: AgentTaskStatus): Boolean = to in allowed.getValue(from)
}

interface AuditMacProvider {
    /** If allowCreate is false, missing/invalidated Keystore key must fail closed. */
    fun computeMac(message: ByteArray, allowCreate: Boolean): ByteArray
}

sealed interface AuditVerification {
    data class Valid(val records: Long) : AuditVerification
    data class Invalid(val reason: String) : AuditVerification
}

interface AuditLogPort {
    suspend fun append(eventType: String, payload: ByteArray, at: Instant): Long
    suspend fun verify(): AuditVerification
}
