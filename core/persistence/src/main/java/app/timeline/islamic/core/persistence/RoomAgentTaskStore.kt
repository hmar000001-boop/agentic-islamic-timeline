package app.timeline.islamic.core.persistence

import androidx.room.withTransaction
import app.timeline.islamic.core.agent.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Durable task store. Room transactions are the serialization boundary for state and leases. */
class RoomAgentTaskStore(private val db: AppDatabase) : AgentTaskStore {
    private val dao get() = db.agentDao()

    override suspend fun createTask(taskId: String, goalType: String, now: Instant) {
        require(taskId.isNotBlank() && goalType.isNotBlank())
        db.withTransaction {
            dao.insertTask(AgentTaskEntity(
                id = taskId, goalType = goalType, status = AgentTaskStatus.RUNNABLE.name,
                createdAtEpochMillis = now.toEpochMilli(), updatedAtEpochMillis = now.toEpochMilli(),
                stateVersion = 1, nextEligibleAtEpochMillis = null
            ))
            appendEvent(taskId, "TASK_CREATED", now)
        }
    }

    override suspend fun latestCheckpoint(taskId: String): TaskCheckpoint? = dao.latestCheckpoint(taskId)?.let {
        TaskCheckpoint(
            taskId = it.taskId, version = it.checkpointVersion, stateSchemaVersion = it.stateSchemaVersion,
            currentStepId = it.currentStepId, encryptedPayload = it.encryptedPayload.copyOf(),
            payloadSha256 = it.payloadHash, createdAt = Instant.ofEpochMilli(it.createdAtEpochMillis)
        )
    }

    override suspend fun getTask(taskId: String): AgentTaskSnapshot? = dao.getTask(taskId)?.let {
        AgentTaskSnapshot(
            id = it.id, goalType = it.goalType, status = AgentTaskStatus.valueOf(it.status),
            stateVersion = it.stateVersion, createdAt = Instant.ofEpochMilli(it.createdAtEpochMillis),
            updatedAt = Instant.ofEpochMilli(it.updatedAtEpochMillis),
            nextEligibleAt = it.nextEligibleAtEpochMillis?.let(Instant::ofEpochMilli)
        )
    }

    override suspend fun reconcileExpiredLeases(now: Instant): Int = db.withTransaction {
        val expired = dao.runningTasksWithExpiredLease(now.toEpochMilli())
        var count = 0
        expired.forEach { task ->
            if (dao.transitionTask(task.id, AgentTaskStatus.RUNNING.name, task.stateVersion,
                    AgentTaskStatus.INTERRUPTED.name, now.toEpochMilli()) == 1) {
                appendEvent(task.id, "TASK_INTERRUPTED_EXPIRED_LEASE", now)
                count++
            }
        }
        count
    }

    override suspend fun resumeInterrupted(taskId: String, expectedVersion: Long, now: Instant): Boolean = db.withTransaction {
        val task = dao.getTask(taskId) ?: return@withTransaction false
        if (task.stateVersion != expectedVersion || task.status != AgentTaskStatus.INTERRUPTED.name) return@withTransaction false
        if (dao.transitionTask(taskId, task.status, expectedVersion, AgentTaskStatus.RUNNABLE.name, now.toEpochMilli()) != 1) return@withTransaction false
        appendEvent(taskId, "TASK_RESUMED_AFTER_INTERRUPTION", now)
        true
    }

    override suspend fun resumeAfterInput(
        taskId: String, expectedVersion: Long, encryptedInput: ByteArray, inputType: String, now: Instant
    ): Boolean = db.withTransaction {
        require(encryptedInput.isNotEmpty() && encryptedInput.size <= 64 * 1024 && inputType.isNotBlank())
        val task = dao.getTask(taskId) ?: return@withTransaction false
        if (task.stateVersion != expectedVersion || task.status != AgentTaskStatus.WAITING_FOR_INPUT.name) return@withTransaction false
        if (dao.transitionTask(taskId, task.status, expectedVersion, AgentTaskStatus.RUNNABLE.name, now.toEpochMilli()) != 1) return@withTransaction false
        dao.appendTaskEvent(AgentTaskEventEntity(
            eventId = UUID.randomUUID().toString(), taskId = taskId,
            sequence = dao.maxEventSequence(taskId) + 1, eventType = "USER_INPUT:$inputType",
            encryptedPayload = encryptedInput.copyOf(), occurredAtEpochMillis = now.toEpochMilli()
        ))
        true
    }

    override suspend fun acquireLease(taskId: String, owner: String, now: Instant, ttlMillis: Long): LeaseAcquireResult {
        require(owner.isNotBlank() && ttlMillis in 1_000..15 * 60 * 1000L)
        val nowMs = now.toEpochMilli()
        val expires = Math.addExact(nowMs, ttlMillis)
        return db.withTransaction {
            val task = dao.getTask(taskId) ?: return@withTransaction LeaseAcquireResult.TaskUnavailable
            if (task.status != AgentTaskStatus.RUNNABLE.name) return@withTransaction LeaseAcquireResult.TaskNotRunnable
            val existing = dao.lease(taskId)
            when {
                existing == null -> {
                    dao.createLeaseIfAbsent(AgentTaskLeaseEntity(taskId, owner, nowMs, nowMs, expires, 1))
                }
                existing.expiresAtEpochMillis <= nowMs -> {
                    val changed = dao.takeExpiredLease(taskId, owner, nowMs, expires)
                    if (changed != 1) return@withTransaction LeaseAcquireResult.LeaseHeld
                }
                else -> return@withTransaction LeaseAcquireResult.LeaseHeld
            }
            val lease = dao.lease(taskId) ?: return@withTransaction LeaseAcquireResult.LeaseHeld
            if (lease.leaseOwner != owner || lease.expiresAtEpochMillis <= nowMs) {
                return@withTransaction LeaseAcquireResult.LeaseHeld
            }
            val transitioned = dao.transitionTask(
                taskId, AgentTaskStatus.RUNNABLE.name, task.stateVersion,
                AgentTaskStatus.RUNNING.name, nowMs
            )
            if (transitioned != 1) {
                dao.releaseLease(taskId, owner, lease.fencingToken, nowMs)
                return@withTransaction LeaseAcquireResult.TaskNotRunnable
            }
            appendEvent(taskId, "TASK_RUNNING", now)
            LeaseAcquireResult.Acquired(
                TaskLease(taskId, owner, now, Instant.ofEpochMilli(expires), lease.fencingToken)
            )
        }
    }

    override suspend fun heartbeat(lease: TaskLease, now: Instant, ttlMillis: Long): Boolean {
        require(ttlMillis in 1_000..15 * 60 * 1000L)
        val nowMs = now.toEpochMilli()
        return dao.heartbeatLease(
            lease.taskId, lease.owner, lease.fencingToken, nowMs, Math.addExact(nowMs, ttlMillis)
        ) == 1
    }

    override suspend fun appendCheckpoint(lease: TaskLease, checkpoint: TaskCheckpoint): Boolean {
        require(checkpoint.taskId == lease.taskId)
        val hash = sha256(checkpoint.encryptedPayload)
        if (hash != checkpoint.payloadSha256) return false
        return db.withTransaction {
            if (!hasLiveLease(lease, checkpoint.createdAt.toEpochMilli())) return@withTransaction false
            val latestVersion = dao.latestCheckpoint(lease.taskId)?.checkpointVersion ?: 0L
            if (checkpoint.version != latestVersion + 1) return@withTransaction false
            dao.insertCheckpoint(AgentTaskCheckpointEntity(
                id = UUID.randomUUID().toString(), taskId = checkpoint.taskId,
                checkpointVersion = checkpoint.version, currentStepId = checkpoint.currentStepId,
                stateSchemaVersion = checkpoint.stateSchemaVersion,
                encryptedPayload = checkpoint.encryptedPayload.copyOf(), payloadHash = checkpoint.payloadSha256,
                createdAtEpochMillis = checkpoint.createdAt.toEpochMilli()
            ))
            appendEvent(lease.taskId, "CHECKPOINT_WRITTEN", checkpoint.createdAt)
            true
        }
    }

    override suspend fun transition(lease: TaskLease, target: AgentTaskStatus, now: Instant, eventType: String): Boolean {
        require(eventType.isNotBlank())
        val nowMs = now.toEpochMilli()
        return db.withTransaction {
            if (!hasLiveLease(lease, nowMs)) return@withTransaction false
            val task = dao.getTask(lease.taskId) ?: return@withTransaction false
            val current = AgentTaskStatus.valueOf(task.status)
            if (current != AgentTaskStatus.RUNNING || !AgentTaskStateMachine.canTransition(current, target)) {
                return@withTransaction false
            }
            if (dao.transitionTask(lease.taskId, current.name, task.stateVersion, target.name, nowMs) != 1) {
                return@withTransaction false
            }
            // Preserve the row and token; never DELETE a lease (which would reset fencing to 1).
            dao.releaseLease(lease.taskId, lease.owner, lease.fencingToken, nowMs)
            appendEvent(lease.taskId, eventType, now)
            true
        }
    }

    override suspend fun releaseLease(lease: TaskLease, now: Instant): Boolean {
        val nowMs = now.toEpochMilli()
        return db.withTransaction {
            if (!hasLiveLease(lease, nowMs)) return@withTransaction false
            val task = dao.getTask(lease.taskId) ?: return@withTransaction false
            if (task.status != AgentTaskStatus.RUNNING.name) return@withTransaction false
            if (dao.transitionTask(lease.taskId, task.status, task.stateVersion, AgentTaskStatus.INTERRUPTED.name, nowMs) != 1) {
                return@withTransaction false
            }
            dao.releaseLease(lease.taskId, lease.owner, lease.fencingToken, nowMs)
            appendEvent(lease.taskId, "TASK_INTERRUPTED", now)
            true
        }
    }

    override suspend fun events(taskId: String): List<AgentTaskEventSnapshot> = dao.taskEvents(taskId).map {
        AgentTaskEventSnapshot(it.eventId, it.taskId, it.sequence, it.eventType, Instant.ofEpochMilli(it.occurredAtEpochMillis))
    }

    internal suspend fun hasLiveLease(lease: TaskLease, nowMs: Long): Boolean {
        val row = dao.lease(lease.taskId) ?: return false
        return row.leaseOwner == lease.owner && row.fencingToken == lease.fencingToken &&
            row.acquiredAtEpochMillis <= nowMs && row.heartbeatAtEpochMillis <= nowMs &&
            row.expiresAtEpochMillis > nowMs
    }

    internal suspend fun appendEvent(taskId: String, eventType: String, at: Instant) {
        val sequence = dao.maxEventSequence(taskId) + 1
        dao.appendTaskEvent(AgentTaskEventEntity(
            eventId = UUID.randomUUID().toString(), taskId = taskId, sequence = sequence,
            eventType = eventType, encryptedPayload = byteArrayOf(), occurredAtEpochMillis = at.toEpochMilli()
        ))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
