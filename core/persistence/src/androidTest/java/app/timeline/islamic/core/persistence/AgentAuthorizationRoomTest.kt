package app.timeline.islamic.core.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.timeline.islamic.core.agent.*
import app.timeline.islamic.core.authorization.AuthorizationEngine
import app.timeline.islamic.core.common.Clock
import app.timeline.islamic.core.security.KeystoreAuditMacProvider
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentAuthorizationRoomTest {
    private lateinit var db: AppDatabase
    private lateinit var taskStore: RoomAgentTaskStore
    private lateinit var authorizationStore: RoomAuthorizationStore
    private lateinit var engine: AuthorizationEngine
    private val clock = MutableClock(Instant.parse("2026-11-07T18:00:00Z"))

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskStore = RoomAgentTaskStore(db)
        authorizationStore = RoomAuthorizationStore(db, KeystoreAuditMacProvider(context, "timeline.test.authorization.${System.nanoTime()}"))
        engine = AuthorizationEngine(authorizationStore, clock)
    }

    @After fun tearDown() { db.close() }

    @Test fun leaseFenceIncreasesAfterReleaseAndResume() = runBlocking {
        taskStore.createTask("task-lease", "HIFZ_REVIEW", clock.now())
        val first = (taskStore.acquireLease("task-lease", "worker-a", clock.now(), 60_000) as LeaseAcquireResult.Acquired).lease
        assertTrue(taskStore.releaseLease(first, clock.now().plusSeconds(1)))
        val interrupted = taskStore.getTask("task-lease")!!
        assertEquals(AgentTaskStatus.INTERRUPTED, interrupted.status)
        clock.advanceSeconds(2)
        assertTrue(taskStore.resumeInterrupted("task-lease", interrupted.stateVersion, clock.now()))
        val second = (taskStore.acquireLease("task-lease", "worker-b", clock.now(), 60_000) as LeaseAcquireResult.Acquired).lease
        assertTrue(second.fencingToken > first.fencingToken)
        assertFalse(taskStore.heartbeat(first, clock.now(), 60_000))
    }

    @Test fun staleFenceCannotCreateActionProposal() = runBlocking {
        taskStore.createTask("task-fence", "HIFZ_REVIEW", clock.now())
        val first = (taskStore.acquireLease("task-fence", "worker-a", clock.now(), 60_000) as LeaseAcquireResult.Acquired).lease
        assertTrue(taskStore.releaseLease(first, clock.now().plusSeconds(1)))
        val interrupted = taskStore.getTask("task-fence")!!
        clock.advanceSeconds(2)
        assertTrue(taskStore.resumeInterrupted("task-fence", interrupted.stateVersion, clock.now()))
        val second = (taskStore.acquireLease("task-fence", "worker-b", clock.now(), 60_000) as LeaseAcquireResult.Acquired).lease
        val result = engine.propose(
            "task-fence", first, "timeline.create", "target", "{}".toByteArray(),
            ImpactClass.LOW, Reversibility.REVERSIBLE, clock.now().plusSeconds(60)
        )
        assertEquals(ActionProposalResult.Unavailable, result)
        assertTrue(second.fencingToken > first.fencingToken)
    }

    @Test fun normalApprovalMustBeReacquiredBeforeClaimAndExecutorCanStartOnlyOnce() = runBlocking {
        taskStore.createTask("task-approval", "TIMELINE_ACTION", clock.now())
        val firstLease = (taskStore.acquireLease("task-approval", "planner", clock.now(), 60_000) as LeaseAcquireResult.Acquired).lease
        val result = engine.propose(
            "task-approval", firstLease, "timeline.create", "event-1", "{\"title\":\"lesson\"}".toByteArray(),
            ImpactClass.LOW, Reversibility.REVERSIBLE, clock.now().plusSeconds(300)
        ) as ActionProposalResult.NeedsApproval
        assertEquals(AgentTaskStatus.WAITING_FOR_APPROVAL, taskStore.getTask("task-approval")!!.status)
        assertEquals("ACTION_PROPOSED_PENDING", db.auditDao().allAscending().single().eventType)
        assertFalse(engine.claimExecution(result.proposal.proposalId, result.proposal.parametersSha256, "idem-a", firstLease) is ExecutionClaimResult.Claimed)

        assertTrue(engine.approve(UserConfirmation(result.proposal.proposalId, result.proposal.parametersSha256, clock.now(), ConfirmationStrength.STANDARD)))
        assertEquals(listOf("ACTION_PROPOSED_PENDING", "ACTION_APPROVED"), db.auditDao().allAscending().map { it.eventType })
        clock.advanceSeconds(1)
        val executionLease = (taskStore.acquireLease("task-approval", "executor", clock.now(), 60_000) as LeaseAcquireResult.Acquired).lease
        val claim = (engine.claimExecution(result.proposal.proposalId, result.proposal.parametersSha256, "idem-a", executionLease) as ExecutionClaimResult.Claimed).claim
        assertTrue(engine.beginExecution(claim))
        assertFalse(engine.beginExecution(claim))
        assertTrue(engine.recordExecutionOutcome(claim, ExecutionOutcome.OUTCOME_UNKNOWN))
        assertEquals(AgentTaskStatus.INTERRUPTED, taskStore.getTask("task-approval")!!.status)
        assertFalse(engine.recordExecutionOutcome(claim, ExecutionOutcome.VERIFIED))
    }

    @Test fun revokedScopedGrantIsRecheckedAtExecutorStart() = runBlocking {
        taskStore.createTask("task-grant", "HIFZ_REVIEW", clock.now())
        val lease = (taskStore.acquireLease("task-grant", "planner", clock.now(), 60_000) as LeaseAcquireResult.Acquired).lease
        val grant = engine.grantHifzInterval("plan-1", 7, "daily-v2", 25, 60_000, AuthorizationEngine.HIFZ_GRANT_POLICY_VERSION)
        val change = HifzIntervalChange("plan-1", 7, 20, "daily-v2")
        val result = engine.proposeHifzIntervalChange("task-grant", lease, change, clock.now().plusSeconds(300)) as ActionProposalResult.CoveredByScopedGrant
        val claim = (engine.claimExecution(result.proposal.proposalId, result.proposal.parametersSha256, "idem-grant", lease) as ExecutionClaimResult.Claimed).claim
        assertTrue(engine.revokeGrant(grant.grantId))
        assertFalse(engine.beginExecution(claim))
        assertEquals(0, authorizationStore.reconcileExecutingExecutions(clock.now()))
        assertEquals("CLAIMED", db.authorizationDao().execution(claim.executionId)!!.state)
        assertEquals(1, authorizationStore.reconcileExecutingExecutions(clock.now()))
        assertEquals("ABORTED_BEFORE_EXECUTION", db.authorizationDao().execution(claim.executionId)!!.state)
    }

    @Test fun expiredLeaseAndExecutingOutboxAreNeverBlindlyReplayed() = runBlocking {
        taskStore.createTask("task-reconcile", "TIMELINE_ACTION", clock.now())
        val lease = (taskStore.acquireLease("task-reconcile", "planner", clock.now(), 60_000) as LeaseAcquireResult.Acquired).lease
        val result = engine.propose(
            "task-reconcile", lease, "timeline.create", "event-reconcile", "{}".toByteArray(),
            ImpactClass.LOW, Reversibility.REVERSIBLE, clock.now().plusSeconds(600)
        ) as ActionProposalResult.NeedsApproval
        assertTrue(engine.approve(UserConfirmation(result.proposal.proposalId, result.proposal.parametersSha256, clock.now(), ConfirmationStrength.STANDARD)))
        clock.advanceSeconds(1)
        val workerLease = (taskStore.acquireLease("task-reconcile", "executor", clock.now(), 60_000) as LeaseAcquireResult.Acquired).lease
        val claim = (engine.claimExecution(result.proposal.proposalId, result.proposal.parametersSha256, "idem-reconcile", workerLease) as ExecutionClaimResult.Claimed).claim
        assertTrue(engine.beginExecution(claim))
        clock.advanceSeconds(1)
        assertEquals(1, authorizationStore.reconcileExecutingExecutions(clock.now()))
        assertEquals("OUTCOME_UNKNOWN", db.authorizationDao().execution(claim.executionId)!!.state)
        assertEquals(AgentTaskStatus.INTERRUPTED, taskStore.getTask("task-reconcile")!!.status)
        assertFalse(engine.beginExecution(claim))
    }

    @Test fun delayedExecutorStartRejectsExpiredLeaseUsingCurrentClock() = runBlocking {
        taskStore.createTask("task-delayed-start", "TIMELINE_ACTION", clock.now())
        val lease = (taskStore.acquireLease("task-delayed-start", "planner", clock.now(), 60_000) as LeaseAcquireResult.Acquired).lease
        val result = engine.propose(
            "task-delayed-start", lease, "timeline.create", "delayed-event", "{\"title\":\"x\"}".toByteArray(),
            ImpactClass.LOW, Reversibility.REVERSIBLE, clock.now().plusSeconds(300)
        ) as ActionProposalResult.NeedsApproval
        assertTrue(engine.approve(UserConfirmation(result.proposal.proposalId, result.proposal.parametersSha256, clock.now(), ConfirmationStrength.STANDARD)))
        clock.advanceSeconds(1)
        val executorLease = (taskStore.acquireLease("task-delayed-start", "executor", clock.now(), 60_000) as LeaseAcquireResult.Acquired).lease
        val claim = (engine.claimExecution(result.proposal.proposalId, result.proposal.parametersSha256, "idem-delayed", executorLease) as ExecutionClaimResult.Claimed).claim
        clock.advanceSeconds(61)
        assertFalse(engine.beginExecution(claim))
        assertEquals("CLAIMED", db.authorizationDao().execution(claim.executionId)!!.state)
        assertEquals(1, authorizationStore.reconcileExecutingExecutions(clock.now()))
        assertEquals("ABORTED_BEFORE_EXECUTION", db.authorizationDao().execution(claim.executionId)!!.state)
    }

    @Test fun expiredScopedGrantIsRecheckedAtExecutorStart() = runBlocking {
        taskStore.createTask("task-expired-grant", "HIFZ_REVIEW", clock.now())
        val lease = (taskStore.acquireLease("task-expired-grant", "planner", clock.now(), 120_000) as LeaseAcquireResult.Acquired).lease
        val grant = engine.grantHifzInterval("plan-expiry", 2, "daily-expiry", 15, AuthorizationEngine.MIN_GRANT_LIFETIME_MILLIS, AuthorizationEngine.HIFZ_GRANT_POLICY_VERSION)
        val proposal = (engine.proposeHifzIntervalChange(
            "task-expired-grant", lease, HifzIntervalChange("plan-expiry", 2, 10, "daily-expiry"), clock.now().plusSeconds(300)
        ) as ActionProposalResult.CoveredByScopedGrant).proposal
        val claim = (engine.claimExecution(proposal.proposalId, proposal.parametersSha256, "idem-expired-grant", lease) as ExecutionClaimResult.Claimed).claim
        clock.advanceSeconds(61)
        assertFalse(engine.beginExecution(claim))
        assertEquals("CLAIMED", db.authorizationDao().execution(claim.executionId)!!.state)
        assertEquals(1, authorizationStore.reconcileExecutingExecutions(clock.now()))
        assertEquals("ABORTED_BEFORE_EXECUTION", db.authorizationDao().execution(claim.executionId)!!.state)
        assertNull(db.authorizationDao().grant(grant.grantId)!!.revokedAtEpochMillis)
    }

    @Test fun expiredPendingApprovalIsExpiredAndTaskRequeued() = runBlocking {
        taskStore.createTask("task-approval-expiry", "TIMELINE_ACTION", clock.now())
        val lease = (taskStore.acquireLease("task-approval-expiry", "planner", clock.now(), 60_000) as LeaseAcquireResult.Acquired).lease
        val result = engine.propose(
            "task-approval-expiry", lease, "timeline.create", "expiry-event", "{}".toByteArray(),
            ImpactClass.LOW, Reversibility.REVERSIBLE, clock.now().plusSeconds(30)
        ) as ActionProposalResult.NeedsApproval
        clock.advanceSeconds(31)
        assertEquals(1, authorizationStore.expirePendingApprovals(clock.now()))
        assertEquals(AgentTaskStatus.RUNNABLE, taskStore.getTask("task-approval-expiry")!!.status)
        assertEquals(ApprovalState.EXPIRED, authorizationStore.approvals(result.proposal.proposalId).single().state)
    }

    @Test fun concurrentExecutorStartConsumesTheClaimExactlyOnce() = runBlocking {
        taskStore.createTask("task-concurrent-start", "TIMELINE_ACTION", clock.now())
        val lease = (taskStore.acquireLease("task-concurrent-start", "planner", clock.now(), 120_000) as LeaseAcquireResult.Acquired).lease
        val result = engine.propose(
            "task-concurrent-start", lease, "timeline.create", "concurrent-event", "{}".toByteArray(),
            ImpactClass.LOW, Reversibility.REVERSIBLE, clock.now().plusSeconds(300)
        ) as ActionProposalResult.NeedsApproval
        assertTrue(engine.approve(UserConfirmation(result.proposal.proposalId, result.proposal.parametersSha256, clock.now(), ConfirmationStrength.STANDARD)))
        clock.advanceSeconds(1)
        val executionLease = (taskStore.acquireLease("task-concurrent-start", "executor", clock.now(), 120_000) as LeaseAcquireResult.Acquired).lease
        val claim = (engine.claimExecution(result.proposal.proposalId, result.proposal.parametersSha256, "idem-concurrent", executionLease) as ExecutionClaimResult.Claimed).claim
        val starts = (1..8).map { async(Dispatchers.IO) { engine.beginExecution(claim) } }.awaitAll()
        assertEquals(1, starts.count { it })
        assertEquals("EXECUTING", db.authorizationDao().execution(claim.executionId)!!.state)
    }

    @Test fun verifiedExecutionOutcomeReleasesLeaseAndRequeuesTask() = runBlocking {
        taskStore.createTask("task-verified", "TIMELINE_ACTION", clock.now())
        val plannerLease = (taskStore.acquireLease("task-verified", "planner", clock.now(), 120_000) as LeaseAcquireResult.Acquired).lease
        val result = engine.propose(
            "task-verified", plannerLease, "timeline.create", "verified-event", "{}".toByteArray(),
            ImpactClass.LOW, Reversibility.REVERSIBLE, clock.now().plusSeconds(300)
        ) as ActionProposalResult.NeedsApproval
        assertTrue(engine.approve(UserConfirmation(result.proposal.proposalId, result.proposal.parametersSha256, clock.now(), ConfirmationStrength.STANDARD)))
        clock.advanceSeconds(1)
        val executorLease = (taskStore.acquireLease("task-verified", "executor", clock.now(), 120_000) as LeaseAcquireResult.Acquired).lease
        val claim = (engine.claimExecution(result.proposal.proposalId, result.proposal.parametersSha256, "idem-verified", executorLease) as ExecutionClaimResult.Claimed).claim
        assertTrue(engine.beginExecution(claim))
        assertTrue(engine.recordExecutionOutcome(claim, ExecutionOutcome.VERIFIED, "verified-receipt".toByteArray()))
        assertEquals(AgentTaskStatus.RUNNABLE, taskStore.getTask("task-verified")!!.status)
        assertEquals("VERIFIED", db.authorizationDao().execution(claim.executionId)!!.state)
        val nextLease = (taskStore.acquireLease("task-verified", "next-worker", clock.now(), 120_000) as LeaseAcquireResult.Acquired).lease
        assertTrue(nextLease.fencingToken > executorLease.fencingToken)
    }

    private class MutableClock(private var value: Instant) : Clock {
        override fun now(): Instant = value
        fun advanceSeconds(seconds: Long) { value = value.plusSeconds(seconds) }
    }
}
