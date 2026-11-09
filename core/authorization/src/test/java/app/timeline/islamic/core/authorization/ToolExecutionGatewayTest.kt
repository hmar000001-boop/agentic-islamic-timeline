package app.timeline.islamic.core.authorization

import app.timeline.islamic.core.agent.*
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ToolExecutionGatewayTest {
    private val parameters = "plan=alpha".toByteArray()
    private val hash = sha(parameters)
    private val claim = ExecutionClaim("exec-1", "proposal-1", hash, "hifz.adjust_review_interval", "plan-1", "idem-1", "task-1", "worker-1", 8, Instant.parse("2026-11-07T18:00:00Z"))

    @Test fun wrongParameterBytesNeverReachHandler(): Unit = runBlocking {
        val auth = FakeAuthorization()
        var called = false
        val gateway = ToolExecutionGateway(auth, mapOf(claim.actionType to ApprovedToolHandler { _, _, _ -> called = true; ToolHandlerResult.Verified() }))
        assertEquals(ToolGatewayResult.ParameterHashMismatch, gateway.execute(claim, "tampered".toByteArray()))
        assertFalse(called)
        assertFalse(auth.started)
    }

    @Test fun missingHandlerDoesNotStartExternalExecution(): Unit = runBlocking {
        val auth = FakeAuthorization()
        val gateway = ToolExecutionGateway(auth, emptyMap())
        assertEquals(ToolGatewayResult.HandlerUnavailable, gateway.execute(claim, parameters))
        assertFalse(auth.started)
    }

    @Test fun handlerRunsOnlyAfterAtomicClaimStart(): Unit = runBlocking {
        val auth = FakeAuthorization()
        var called = false
        val gateway = ToolExecutionGateway(auth, mapOf(claim.actionType to ApprovedToolHandler { _, _, key ->
            called = true
            assertEquals("idem-1", key)
            ToolHandlerResult.Verified("receipt".toByteArray())
        }))
        assertEquals(ToolGatewayResult.Finished(ExecutionOutcome.VERIFIED), gateway.execute(claim, parameters))
        assertTrue(called)
        assertEquals(ExecutionOutcome.VERIFIED, auth.outcome)
    }

    @Test fun failedClaimStartNeverCallsHandler(): Unit = runBlocking {
        val auth = FakeAuthorization().apply { allowBegin = false }
        var called = false
        val gateway = ToolExecutionGateway(auth, mapOf(claim.actionType to ApprovedToolHandler { _, _, _ -> called = true; ToolHandlerResult.Verified() }))
        assertEquals(ToolGatewayResult.ClaimRejected, gateway.execute(claim, parameters))
        assertFalse(called)
    }

    @Test fun cancellationAfterExecutorStartAttemptsToPersistUnknownOutcome(): Unit = runBlocking {
        val auth = FakeAuthorization()
        val cancellationParameters = "{}".toByteArray()
        val cancellationClaim = claim.copy(proposalSha256 = sha(cancellationParameters))
        val gateway = ToolExecutionGateway(auth, mapOf(cancellationClaim.actionType to ApprovedToolHandler { _, _, _ ->
            throw CancellationException("simulated coroutine cancellation after side-effect gate")
        }))
        try {
            gateway.execute(cancellationClaim, cancellationParameters)
            fail("Cancellation must propagate to the caller")
        } catch (_: CancellationException) { }
        assertEquals(ExecutionOutcome.OUTCOME_UNKNOWN, auth.outcome)
    }

    @Test fun handlerExceptionIsRecordedAsUnknownNotRetried(): Unit = runBlocking {
        val auth = FakeAuthorization()
        var calls = 0
        val gateway = ToolExecutionGateway(auth, mapOf(claim.actionType to ApprovedToolHandler { _, _, _ ->
            calls++
            throw IllegalStateException("simulated provider failure after call")
        }))
        assertEquals(ToolGatewayResult.Finished(ExecutionOutcome.OUTCOME_UNKNOWN), gateway.execute(claim, parameters))
        assertEquals(ExecutionOutcome.OUTCOME_UNKNOWN, auth.outcome)
        assertEquals(1, calls)
    }

    private class FakeAuthorization : AuthorizationServicePort {
        var allowBegin = true
        var started = false
        var outcome: ExecutionOutcome? = null
        override suspend fun propose(taskId: String, lease: TaskLease, actionType: String, targetId: String?, canonicalParameters: ByteArray, impactClass: ImpactClass, reversibility: Reversibility, expiresAt: Instant) = ActionProposalResult.Unavailable
        override suspend fun proposeHifzIntervalChange(taskId: String, lease: TaskLease, change: HifzIntervalChange, expiresAt: Instant) = ActionProposalResult.Unavailable
        override suspend fun approve(confirmation: UserConfirmation, approvalLifetimeMillis: Long) = false
        override suspend fun reject(proposalId: String, proposalSha256: String) = false
        override suspend fun claimExecution(proposalId: String, expectedProposalSha256: String, idempotencyKey: String, lease: TaskLease) = ExecutionClaimResult.NotFound
        override suspend fun beginExecution(claim: ExecutionClaim): Boolean { if (!allowBegin || started) return false; started = true; return true }
        override suspend fun recordExecutionOutcome(claim: ExecutionClaim, outcome: ExecutionOutcome, receipt: ByteArray?): Boolean { if (!started || this.outcome != null) return false; this.outcome = outcome; return true }
    }

    private companion object {
        fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
