package app.timeline.islamic.core.persistence

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local state for the encrypted attachment store's startup reconciliation.
 * A failed attempt is retained until an explicit retry; it is never mistaken for an empty store.
 */
sealed interface AttachmentRecoveryState {
    data object NotStarted : AttachmentRecoveryState
    data object Running : AttachmentRecoveryState
    data class Ready(val report: AttachmentRecoveryReport) : AttachmentRecoveryState
    data class Failed(val failureCode: String) : AttachmentRecoveryState
}

/**
 * Serializes startup recovery and retries. The coordinator does not expose exception messages,
 * attachment IDs or paths through its state; callers receive only a non-sensitive failure code.
 */
class AttachmentRecoveryCoordinator(
    private val reconcile: suspend () -> AttachmentRecoveryReport
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<AttachmentRecoveryState>(AttachmentRecoveryState.NotStarted)
    val state: StateFlow<AttachmentRecoveryState> = mutableState.asStateFlow()

    /** Starts recovery once. Repeated calls return the completed or failed state without rerunning it. */
    suspend fun initialize(): AttachmentRecoveryState = mutex.withLock {
        when (val current = mutableState.value) {
            AttachmentRecoveryState.NotStarted -> runRecovery()
            AttachmentRecoveryState.Running,
            is AttachmentRecoveryState.Ready,
            is AttachmentRecoveryState.Failed -> current
        }
    }

    /** Retries only a failed recovery. It cannot race a live attempt or reset a healthy state. */
    suspend fun retry(): AttachmentRecoveryState = mutex.withLock {
        when (val current = mutableState.value) {
            is AttachmentRecoveryState.Failed -> runRecovery()
            AttachmentRecoveryState.NotStarted,
            AttachmentRecoveryState.Running,
            is AttachmentRecoveryState.Ready -> current
        }
    }

    private suspend fun runRecovery(): AttachmentRecoveryState {
        mutableState.value = AttachmentRecoveryState.Running
        return try {
            AttachmentRecoveryState.Ready(reconcile()).also { mutableState.value = it }
        } catch (cancelled: CancellationException) {
            // Cancellation is not a completed failure. A later process-owned retry may start cleanly.
            mutableState.value = AttachmentRecoveryState.NotStarted
            throw cancelled
        } catch (failure: Exception) {
            // Do not leak exception messages, paths or private metadata to UI/state observers.
            AttachmentRecoveryState.Failed(
                failureCode = failure::class.simpleName ?: "ATTACHMENT_RECOVERY_FAILED"
            ).also { mutableState.value = it }
        }
    }
}
