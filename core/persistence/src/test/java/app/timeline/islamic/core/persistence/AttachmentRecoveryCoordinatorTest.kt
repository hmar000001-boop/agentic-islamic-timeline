package app.timeline.islamic.core.persistence

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AttachmentRecoveryCoordinatorTest {
    private val cleanReport = AttachmentRecoveryReport(
        orphanedEncryptedFilesDeleted = 0,
        abandonedTemporaryFilesDeleted = 0,
        missingDatabaseReferencedFiles = emptyList()
    )

    @Test
    fun initializeRunsOnceAndRetainsReadyReport(): Unit = runBlocking {
        val calls = AtomicInteger()
        val coordinator = AttachmentRecoveryCoordinator {
            calls.incrementAndGet()
            cleanReport
        }

        assertEquals(AttachmentRecoveryState.Ready(cleanReport), coordinator.initialize())
        assertEquals(AttachmentRecoveryState.Ready(cleanReport), coordinator.initialize())
        assertEquals(1, calls.get())
    }

    @Test
    fun failureIsVisibleAndOnlyAnExplicitRetryRunsAgain(): Unit = runBlocking {
        val calls = AtomicInteger()
        val coordinator = AttachmentRecoveryCoordinator {
            if (calls.incrementAndGet() == 1) {
                throw IllegalStateException("private path must not reach observable state")
            }
            cleanReport
        }

        val failed = coordinator.initialize()
        assertTrue(failed is AttachmentRecoveryState.Failed)
        assertEquals("IllegalStateException", (failed as AttachmentRecoveryState.Failed).failureCode)
        assertEquals(failed, coordinator.state.value)
        assertEquals(failed, coordinator.initialize())
        assertEquals(1, calls.get())

        assertEquals(AttachmentRecoveryState.Ready(cleanReport), coordinator.retry())
        assertEquals(2, calls.get())
    }

    @Test
    fun cancellationResetsStateAndPropagates(): Unit = runBlocking {
        val calls = AtomicInteger()
        val coordinator = AttachmentRecoveryCoordinator {
            calls.incrementAndGet()
            if (calls.get() == 1) throw kotlinx.coroutines.CancellationException("cancel test")
            cleanReport
        }

        var cancellationPropagated = false
        try {
            coordinator.initialize()
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancellationPropagated = true
        }
        assertTrue(cancellationPropagated)
        assertEquals(AttachmentRecoveryState.NotStarted, coordinator.state.value)
        assertEquals(AttachmentRecoveryState.Ready(cleanReport), coordinator.initialize())
        assertEquals(2, calls.get())
    }

    @Test
    fun concurrentInitializationSharesOneSuccessfulRecovery(): Unit = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val coordinator = AttachmentRecoveryCoordinator {
            calls.incrementAndGet()
            started.complete(Unit)
            finish.await()
            cleanReport
        }

        val first = async { coordinator.initialize() }
        started.await()
        val second = async { coordinator.initialize() }
        finish.complete(Unit)

        assertEquals(AttachmentRecoveryState.Ready(cleanReport), first.await())
        assertEquals(AttachmentRecoveryState.Ready(cleanReport), second.await())
        assertEquals(1, calls.get())
    }
}
