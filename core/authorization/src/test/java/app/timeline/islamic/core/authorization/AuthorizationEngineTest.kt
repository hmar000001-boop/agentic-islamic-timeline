package app.timeline.islamic.core.authorization

import app.timeline.islamic.core.agent.*
import app.timeline.islamic.core.common.Clock
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AuthorizationEngineTest {
    private val now = Instant.parse("2026-11-07T18:00:00Z")
    private val clock = MutableClock(now)
    private val store = FakeAuthorizationStore()
    private var idCounter = 0
    private val engine = AuthorizationEngine(store, clock) { "id-${++idCounter}" }
    private val lease = TaskLease("task-1", "worker-1", now, now.plusSeconds(90), 7)

    @Test fun ordinaryMutationCreatesPendingApprovalAndDoesNotExecute(): Unit = runBlocking {
        val result = engine.propose("task-1", lease, "timeline.create", "item-1", bytes("{}"), ImpactClass.LOW, Reversibility.REVERSIBLE, now.plusSeconds(60))
        assertTrue(result is ActionProposalResult.NeedsApproval)
        assertEquals("WAITING_FOR_APPROVAL", store.taskState)
        assertNull(store.claim)
        assertEquals(ApprovalState.PENDING, store.approval?.state)
    }

    @Test fun expiredProposalIsNotPersisted(): Unit = runBlocking {
        val result = engine.propose("task-1", lease, "timeline.create", "item-1", bytes("{}"), ImpactClass.LOW, Reversibility.REVERSIBLE, now)
        assertEquals(ActionProposalResult.Unavailable, result)
        assertNull(store.savedProposal)
    }

    @Test fun emptyCanonicalParametersAreRejected(): Unit = runBlocking {
        val result = engine.propose("task-1", lease, "timeline.create", "item-1", byteArrayOf(), ImpactClass.LOW, Reversibility.REVERSIBLE, now.plusSeconds(60))
        assertEquals(ActionProposalResult.Unavailable, result)
    }

    @Test fun canonicalParameterBufferIsCopiedBeforePersistence(): Unit = runBlocking {
        val payload = bytes("{\"x\":1}")
        val result = engine.propose("task-1", lease, "timeline.create", "item-1", payload, ImpactClass.LOW, Reversibility.REVERSIBLE, now.plusSeconds(60))
        payload[0] = 0
        val proposal = (result as ActionProposalResult.NeedsApproval).proposal
        assertEquals(sha(bytes("{\"x\":1}")), proposal.parametersSha256)
        assertEquals("{\"x\":1}", store.canonicalBytes!!.toString(Charsets.UTF_8))
    }

    @Test fun standardConfirmationCannotApproveHighImpactAction(): Unit = runBlocking {
        val proposal = needsApproval(ImpactClass.HIGH)
        val confirmation = UserConfirmation(proposal.proposalId, proposal.parametersSha256, now, ConfirmationStrength.STANDARD)
        assertFalse(engine.approve(confirmation))
        assertEquals(ApprovalState.PENDING, store.approval?.state)
    }

    @Test fun strongConfirmationCanApproveHighImpactAction(): Unit = runBlocking {
        val proposal = needsApproval(ImpactClass.HIGH)
        val confirmation = UserConfirmation(proposal.proposalId, proposal.parametersSha256, now, ConfirmationStrength.STRONG)
        assertTrue(engine.approve(confirmation))
        assertEquals(ApprovalState.APPROVED, store.approval?.state)
        assertEquals("RUNNABLE", store.taskState)
    }

    @Test fun alteredProposalHashCannotBeApproved(): Unit = runBlocking {
        val proposal = needsApproval()
        val confirmation = UserConfirmation(proposal.proposalId, "0".repeat(64), now, ConfirmationStrength.STANDARD)
        assertFalse(engine.approve(confirmation))
        assertEquals(ApprovalState.PENDING, store.approval?.state)
    }

    @Test fun confirmationOlderThanTwoMinutesIsRejected(): Unit = runBlocking {
        val proposal = needsApproval()
        val confirmation = UserConfirmation(proposal.proposalId, proposal.parametersSha256, now.minusSeconds(121), ConfirmationStrength.STANDARD)
        assertFalse(engine.approve(confirmation))
    }

    @Test fun confirmationFromFutureIsRejected(): Unit = runBlocking {
        val proposal = needsApproval()
        val confirmation = UserConfirmation(proposal.proposalId, proposal.parametersSha256, now.plusSeconds(1), ConfirmationStrength.STANDARD)
        assertFalse(engine.approve(confirmation))
    }

    @Test fun approvalLifetimeCannotExceedConfiguredBound(): Unit = runBlocking {
        val proposal = needsApproval()
        val confirmation = UserConfirmation(proposal.proposalId, proposal.parametersSha256, now, ConfirmationStrength.STANDARD)
        try {
            engine.approve(confirmation, AuthorizationEngine.MAX_APPROVAL_LIFETIME_MILLIS + 1)
            fail("Expected approval lifetime validation to fail")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun scopedHifzGrantCannotBeUsedBeforeItsCreationTime() {
        val created = Instant.parse("2026-11-07T12:00:00Z")
        val change = HifzIntervalChange("plan-future", 1, 10, "schedule-v1")
        val grant = HifzIntervalGrant(
            grantId = "future-grant", planId = change.planId,
            immutableParametersSha256 = change.immutableFingerprint(), maxAbsoluteDeltaMinutes = 20,
            createdAt = created, expiresAt = created.plusSeconds(3600), revokedAt = null,
            policyVersion = AuthorizationEngine.HIFZ_GRANT_POLICY_VERSION
        )
        assertFalse(grant.matches(change, created.minusSeconds(1)))
        assertTrue(grant.matches(change, created))
    }

    @Test fun scopedHifzGrantCoversOnlyMatchingPlanRevisionAndSchedule(): Unit = runBlocking {
        engine.grantHifzInterval("plan-1", 4, "daily-v1", 30, AuthorizationEngine.MIN_GRANT_LIFETIME_MILLIS, AuthorizationEngine.HIFZ_GRANT_POLICY_VERSION)
        val change = HifzIntervalChange("plan-1", 4, 25, "daily-v1")
        val result = engine.proposeHifzIntervalChange("task-1", lease, change, now.plusSeconds(60))
        assertTrue(result is ActionProposalResult.CoveredByScopedGrant)
        assertEquals("RUNNING", store.taskState)
        assertEquals(ApprovalState.APPROVED, store.approval?.state)
        assertNotNull(store.approval?.sourceGrantId)
    }

    @Test fun scopedHifzGrantRejectsDeltaAboveMaximum(): Unit = runBlocking {
        engine.grantHifzInterval("plan-1", 4, "daily-v1", 30, AuthorizationEngine.MIN_GRANT_LIFETIME_MILLIS, AuthorizationEngine.HIFZ_GRANT_POLICY_VERSION)
        val result = engine.proposeHifzIntervalChange("task-1", lease, HifzIntervalChange("plan-1", 4, 31, "daily-v1"), now.plusSeconds(60))
        assertTrue(result is ActionProposalResult.NeedsApproval)
    }

    @Test fun scopedHifzGrantRejectsChangedRevisionAndProfile(): Unit = runBlocking {
        engine.grantHifzInterval("plan-1", 4, "daily-v1", 30, AuthorizationEngine.MIN_GRANT_LIFETIME_MILLIS, AuthorizationEngine.HIFZ_GRANT_POLICY_VERSION)
        val revisionChange = engine.proposeHifzIntervalChange("task-1", lease, HifzIntervalChange("plan-1", 5, 10, "daily-v1"), now.plusSeconds(60))
        assertTrue(revisionChange is ActionProposalResult.NeedsApproval)
        store.resetTaskRunning()
        val profileChange = engine.proposeHifzIntervalChange("task-1", lease, HifzIntervalChange("plan-1", 4, 10, "weekly-v2"), now.plusSeconds(60))
        assertTrue(profileChange is ActionProposalResult.NeedsApproval)
    }

    @Test fun revokedGrantStopsCoveringFutureProposals(): Unit = runBlocking {
        val grant = engine.grantHifzInterval("plan-1", 4, "daily-v1", 30, AuthorizationEngine.MIN_GRANT_LIFETIME_MILLIS, AuthorizationEngine.HIFZ_GRANT_POLICY_VERSION)
        assertTrue(engine.revokeGrant(grant.grantId))
        val result = engine.proposeHifzIntervalChange("task-1", lease, HifzIntervalChange("plan-1", 4, 10, "daily-v1"), now.plusSeconds(60))
        assertTrue(result is ActionProposalResult.NeedsApproval)
    }

    @Test fun obsoleteGrantPolicyCannotAuthorizeHifzChange(): Unit = runBlocking {
        val legacy = HifzIntervalChange("plan-1", 4, 1, "daily-v1")
        store.grant = HifzIntervalGrant(
            "legacy-grant", "plan-1", legacy.immutableFingerprint(), 30, now,
            now.plusSeconds(300), null, "hifz-interval.v0"
        )
        val result = engine.proposeHifzIntervalChange("task-1", lease, legacy.copy(requestedDeltaMinutes = 10), now.plusSeconds(60))
        assertTrue(result is ActionProposalResult.NeedsApproval)
        assertNull(store.approval?.sourceGrantId)
    }

    @Test fun persistentGrantLifetimeIsBounded(): Unit = runBlocking {
        try {
            engine.grantHifzInterval("plan-1", 4, "daily-v1", 30, AuthorizationEngine.MAX_GRANT_LIFETIME_MILLIS + 1, AuthorizationEngine.HIFZ_GRANT_POLICY_VERSION)
            fail("Expected grant lifetime validation to fail")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun claimCannotProceedWithoutApproval(): Unit = runBlocking {
        val proposal = needsApproval()
        val result = engine.claimExecution(proposal.proposalId, proposal.parametersSha256, "idempotency-1", lease)
        assertFalse(result is ExecutionClaimResult.Claimed)
        assertNull(store.claim)
    }

    @Test fun claimAndBeginExecutionAreOneShot(): Unit = runBlocking {
        val proposal = needsApproval()
        assertTrue(engine.approve(UserConfirmation(proposal.proposalId, proposal.parametersSha256, now, ConfirmationStrength.STANDARD)))
        store.resetTaskRunning()
        val first = engine.claimExecution(proposal.proposalId, proposal.parametersSha256, "idempotency-1", lease)
        val claim = (first as ExecutionClaimResult.Claimed).claim
        assertTrue(engine.beginExecution(claim))
        assertFalse(engine.beginExecution(claim))
    }

    @Test fun delayedExecutorStartRechecksApprovalAndProposalExpiry(): Unit = runBlocking {
        val proposal = needsApproval()
        assertTrue(engine.approve(UserConfirmation(proposal.proposalId, proposal.parametersSha256, now, ConfirmationStrength.STANDARD)))
        store.resetTaskRunning()
        val claim = (engine.claimExecution(proposal.proposalId, proposal.parametersSha256, "idem-expiry", lease) as ExecutionClaimResult.Claimed).claim
        clock.advanceSeconds(61)
        assertFalse(engine.beginExecution(claim))
        assertFalse(store.started)
    }

    @Test fun delayedExecutorStartRechecksScopedGrantExpiry(): Unit = runBlocking {
        engine.grantHifzInterval("plan-1", 4, "daily-v1", 30, AuthorizationEngine.MIN_GRANT_LIFETIME_MILLIS, AuthorizationEngine.HIFZ_GRANT_POLICY_VERSION)
        val result = engine.proposeHifzIntervalChange("task-1", lease, HifzIntervalChange("plan-1", 4, 10, "daily-v1"), now.plusSeconds(120))
        val proposal = (result as ActionProposalResult.CoveredByScopedGrant).proposal
        val claim = (engine.claimExecution(proposal.proposalId, proposal.parametersSha256, "idem-grant-expiry", lease) as ExecutionClaimResult.Claimed).claim
        clock.advanceSeconds(61)
        assertFalse(engine.beginExecution(claim))
        assertFalse(store.started)
    }

    @Test fun verifiedExecutionOutcomeRequeuesTaskForNextAgentStep(): Unit = runBlocking {
        val proposal = needsApproval()
        assertTrue(engine.approve(UserConfirmation(proposal.proposalId, proposal.parametersSha256, now, ConfirmationStrength.STANDARD)))
        store.resetTaskRunning()
        val claim = (engine.claimExecution(proposal.proposalId, proposal.parametersSha256, "idem-verified", lease) as ExecutionClaimResult.Claimed).claim
        assertTrue(engine.beginExecution(claim))
        assertTrue(engine.recordExecutionOutcome(claim, ExecutionOutcome.VERIFIED, "receipt".toByteArray()))
        assertEquals("RUNNABLE", store.taskState)
        assertEquals("VERIFIED", store.outcome)
    }

    @Test fun unknownOutcomeCannotBeRecordedTwice(): Unit = runBlocking {
        val proposal = needsApproval()
        engine.approve(UserConfirmation(proposal.proposalId, proposal.parametersSha256, now, ConfirmationStrength.STANDARD))
        store.resetTaskRunning()
        val claim = (engine.claimExecution(proposal.proposalId, proposal.parametersSha256, "idem-2", lease) as ExecutionClaimResult.Claimed).claim
        assertTrue(engine.beginExecution(claim))
        assertTrue(engine.recordExecutionOutcome(claim, ExecutionOutcome.OUTCOME_UNKNOWN))
        assertFalse(engine.recordExecutionOutcome(claim, ExecutionOutcome.VERIFIED))
        assertEquals("INTERRUPTED", store.taskState)
    }

    private suspend fun needsApproval(impact: ImpactClass = ImpactClass.LOW): ActionProposal {
        val result = engine.propose("task-1", lease, "timeline.create", "item-1", bytes("{\"ok\":true}"), impact, Reversibility.REVERSIBLE, now.plusSeconds(60))
        return (result as ActionProposalResult.NeedsApproval).proposal
    }

    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)
    private fun sha(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class MutableClock(private var current: Instant) : Clock {
        override fun now(): Instant = current
        fun advanceSeconds(seconds: Long) { current = current.plusSeconds(seconds) }
    }

    private class FakeAuthorizationStore : AuthorizationStore {
        var taskState = "RUNNING"
        var savedProposal: ActionProposal? = null
        var canonicalBytes: ByteArray? = null
        var approval: ApprovalSnapshot? = null
        var grant: HifzIntervalGrant? = null
        var claim: ExecutionClaim? = null
        var started = false
        var outcome: String? = null

        fun resetTaskRunning() { taskState = "RUNNING" }

        override suspend fun persistProposalAndApproval(proposal: ActionProposal, canonicalParameters: ByteArray, approvalId: String, lease: TaskLease, now: Instant): Boolean {
            if (taskState != "RUNNING" || lease.expiresAt <= now || sha(canonicalParameters) != proposal.parametersSha256) return false
            savedProposal = proposal
            canonicalBytes = canonicalParameters.copyOf()
            approval = ApprovalSnapshot(approvalId, proposal.proposalId, proposal.parametersSha256, ApprovalState.PENDING, proposal.expiresAt, null)
            taskState = "WAITING_FOR_APPROVAL"
            return true
        }

        override suspend fun persistProposalUsingHifzGrant(proposal: ActionProposal, canonicalParameters: ByteArray, approvalId: String, grantId: String, lease: TaskLease, now: Instant): Boolean {
            if (taskState != "RUNNING" || lease.expiresAt <= now || sha(canonicalParameters) != proposal.parametersSha256) return false
            val change = decode(canonicalParameters) ?: return false
            val matching = grant?.takeIf { it.grantId == grantId && it.matches(change, now) } ?: return false
            savedProposal = proposal
            canonicalBytes = canonicalParameters.copyOf()
            approval = ApprovalSnapshot(approvalId, proposal.proposalId, proposal.parametersSha256, ApprovalState.APPROVED, minOf(proposal.expiresAt, matching.expiresAt), now, grantId)
            return true
        }

        override suspend fun proposal(proposalId: String) = savedProposal?.takeIf { it.proposalId == proposalId }

        override suspend fun approveExact(confirmation: UserConfirmation, approvalExpiresAt: Instant): Boolean {
            val proposal = savedProposal ?: return false
            val current = approval ?: return false
            if (taskState != "WAITING_FOR_APPROVAL" || proposal.proposalId != confirmation.proposalId || proposal.parametersSha256 != confirmation.proposalSha256 || current.state != ApprovalState.PENDING) return false
            approval = current.copy(state = ApprovalState.APPROVED, decisionAt = confirmation.confirmedAt, expiresAt = minOf(current.expiresAt, approvalExpiresAt))
            taskState = "RUNNABLE"
            return true
        }

        override suspend fun rejectExact(proposalId: String, proposalSha256: String, at: Instant): Boolean {
            val current = approval ?: return false
            if (current.proposalId != proposalId || current.proposalSha256 != proposalSha256 || current.state != ApprovalState.PENDING) return false
            approval = current.copy(state = ApprovalState.REJECTED, decisionAt = at)
            taskState = "CANCELLED"
            return true
        }

        override suspend fun createHifzGrant(grant: HifzIntervalGrant): Boolean { this.grant = grant; return true }
        override suspend fun revokeGrant(grantId: String, at: Instant): Boolean {
            val current = grant ?: return false
            if (current.grantId != grantId || current.revokedAt != null) return false
            grant = current.copy(revokedAt = at)
            return true
        }
        override suspend fun matchingHifzGrant(change: HifzIntervalChange, now: Instant) = grant?.takeIf { it.matches(change, now) }

        override suspend fun beginClaimedExecution(claim: ExecutionClaim, now: Instant): Boolean {
            val proposal = savedProposal ?: return false
            val currentApproval = approval ?: return false
            if (this.claim != claim || started || taskState != "RUNNING" ||
                currentApproval.state != ApprovalState.APPROVED || currentApproval.expiresAt <= now ||
                proposal.expiresAt <= now) return false
            if (currentApproval.sourceGrantId != null) {
                val currentGrant = grant ?: return false
                val change = decode(canonicalBytes ?: return false) ?: return false
                if (currentGrant.grantId != currentApproval.sourceGrantId || !currentGrant.matches(change, now)) return false
            }
            started = true
            return true
        }

        override suspend fun claimApprovedExecution(proposalId: String, expectedProposalSha256: String, idempotencyKey: String, lease: TaskLease, now: Instant): ExecutionClaimResult {
            val proposal = savedProposal ?: return ExecutionClaimResult.NotFound
            val current = approval ?: return ExecutionClaimResult.ApprovalMissing
            if (proposal.proposalId != proposalId) return ExecutionClaimResult.NotFound
            if (proposal.parametersSha256 != expectedProposalSha256) return ExecutionClaimResult.HashMismatch
            if (taskState != "RUNNING") return ExecutionClaimResult.TaskNotRunning
            if (current.state != ApprovalState.APPROVED) return ExecutionClaimResult.ApprovalMissing
            if (current.expiresAt <= now) return ExecutionClaimResult.ApprovalExpired
            val created = ExecutionClaim("exec-1", proposalId, expectedProposalSha256, proposal.actionType, proposal.targetId, idempotencyKey, proposal.taskId, lease.owner, lease.fencingToken, now)
            if (claim != null) return ExecutionClaimResult.AlreadyClaimed
            claim = created
            return ExecutionClaimResult.Claimed(created)
        }

        override suspend fun markExecutionOutcome(claim: ExecutionClaim, terminalState: String, receipt: ByteArray?, now: Instant): Boolean {
            if (this.claim != claim || !started || outcome != null) return false
            outcome = terminalState
            if (terminalState == "OUTCOME_UNKNOWN" || terminalState == "APPLIED_UNVERIFIED") taskState = "INTERRUPTED"
            else taskState = "RUNNABLE"
            return true
        }
        override suspend fun reconcileExecutingExecutions(now: Instant) = 0
        override suspend fun expirePendingApprovals(now: Instant) = 0
        override suspend fun approvals(proposalId: String) = listOfNotNull(approval)

        private fun decode(bytes: ByteArray): HifzIntervalChange? = try {
            val v = bytes.toString(Charsets.UTF_8).split('\u001f')
            HifzIntervalChange(v[1], v[2].toLong(), v[3].toInt(), v[4])
        } catch (_: Exception) { null }
        private fun sha(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
