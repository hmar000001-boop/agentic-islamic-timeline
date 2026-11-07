package app.timeline.islamic.core.authorization

import app.timeline.islamic.core.agent.*
import app.timeline.islamic.core.common.Clock
import app.timeline.islamic.core.common.SystemClock
import java.security.MessageDigest
import java.util.UUID
import java.time.Instant

/**
 * The application-side authority for state-changing actions. Model output is only input data;
 * it can never invoke a tool. A tool executor must receive an ExecutionClaim issued by the store.
 */
class AuthorizationEngine(
    private val store: AuthorizationStore,
    private val clock: Clock = SystemClock,
    private val hifzGrantPolicyVersion: String = HIFZ_GRANT_POLICY_VERSION,
    private val id: () -> String = { UUID.randomUUID().toString() }
) : AuthorizationServicePort {
    override suspend fun propose(
        taskId: String,
        lease: TaskLease,
        actionType: String,
        targetId: String?,
        canonicalParameters: ByteArray,
        impactClass: ImpactClass,
        reversibility: Reversibility,
        expiresAt: Instant
    ): ActionProposalResult {
        val now = clock.now()
        if (!expiresAt.isAfter(now) || canonicalParameters.isEmpty() || canonicalParameters.size > MAX_PARAMETER_BYTES) {
            return ActionProposalResult.Unavailable
        }
        val proposal = ActionProposal(
            proposalId = id(), taskId = taskId, actionType = actionType, targetId = targetId,
            parametersSha256 = sha256(canonicalParameters), createdAt = now, expiresAt = expiresAt,
            impactClass = impactClass, reversibility = reversibility
        )
        val approvalId = id()
        if (!store.persistProposalAndApproval(proposal, canonicalParameters.copyOf(), approvalId, lease, now)) {
            return ActionProposalResult.Unavailable
        }
        return ActionProposalResult.NeedsApproval(proposal, approvalId)
    }

    /** Only this exact low-risk action is eligible for persistent scope in Phase 3. */
    override suspend fun proposeHifzIntervalChange(
        taskId: String,
        lease: TaskLease,
        change: HifzIntervalChange,
        expiresAt: Instant
    ): ActionProposalResult {
        val now = clock.now()
        if (!expiresAt.isAfter(now)) return ActionProposalResult.Unavailable
        val bytes = change.canonicalBytes()
        val proposal = ActionProposal(
            proposalId = id(), taskId = taskId, actionType = HIFZ_INTERVAL_ACTION,
            targetId = change.planId, parametersSha256 = sha256(bytes), createdAt = now,
            expiresAt = expiresAt, impactClass = ImpactClass.LOW,
            reversibility = Reversibility.REVERSIBLE
        )
        val grant = store.matchingHifzGrant(change, now)
        if (grant != null && grant.policyVersion == hifzGrantPolicyVersion &&
            store.persistProposalUsingHifzGrant(proposal, bytes, id(), grant.grantId, lease, now)) {
            return ActionProposalResult.CoveredByScopedGrant(proposal, grant.grantId)
        }
        val approvalId = id()
        if (!store.persistProposalAndApproval(proposal, bytes, approvalId, lease, now)) return ActionProposalResult.Unavailable
        return ActionProposalResult.NeedsApproval(proposal, approvalId)
    }

    override suspend fun approve(confirmation: UserConfirmation, approvalLifetimeMillis: Long): Boolean {
        require(approvalLifetimeMillis in 1_000..MAX_APPROVAL_LIFETIME_MILLIS)
        val proposal = store.proposal(confirmation.proposalId) ?: return false
        val now = clock.now()
        if (confirmation.confirmedAt.isAfter(now) || confirmation.confirmedAt.isBefore(now.minusSeconds(120)) || !proposal.expiresAt.isAfter(now)) return false
        if (confirmation.proposalSha256 != proposal.parametersSha256) return false
        if (requiresStrongConfirmation(proposal) && confirmation.strength != ConfirmationStrength.STRONG) return false
        return store.approveExact(confirmation, minOf(proposal.expiresAt, now.plusMillis(approvalLifetimeMillis)))
    }

    override suspend fun reject(proposalId: String, proposalSha256: String): Boolean =
        store.rejectExact(proposalId, proposalSha256, clock.now())

    suspend fun grantHifzInterval(
        planId: String,
        basePlanRevision: Long,
        scheduleProfileId: String,
        maxAbsoluteDeltaMinutes: Int,
        lifetimeMillis: Long,
        policyVersion: String
    ): HifzIntervalGrant {
        val now = clock.now()
        require(planId.isNotBlank())
        require(basePlanRevision > 0 && scheduleProfileId.isNotBlank())
        val immutableParametersSha256 = HifzIntervalChange(planId, basePlanRevision, 1, scheduleProfileId).immutableFingerprint()
        require(maxAbsoluteDeltaMinutes in 1..MAX_GRANT_DELTA_MINUTES)
        require(lifetimeMillis in MIN_GRANT_LIFETIME_MILLIS..MAX_GRANT_LIFETIME_MILLIS)
        require(policyVersion == hifzGrantPolicyVersion) { "Grant policy version is not the active policy" }
        val grant = HifzIntervalGrant(
            grantId = id(), planId = planId, immutableParametersSha256 = immutableParametersSha256,
            maxAbsoluteDeltaMinutes = maxAbsoluteDeltaMinutes, createdAt = now,
            expiresAt = now.plusMillis(lifetimeMillis), revokedAt = null, policyVersion = policyVersion
        )
        check(store.createHifzGrant(grant)) { "Could not persist scoped grant" }
        return grant
    }

    suspend fun revokeGrant(grantId: String): Boolean = store.revokeGrant(grantId, clock.now())

    override suspend fun claimExecution(
        proposalId: String,
        expectedProposalSha256: String,
        idempotencyKey: String,
        lease: TaskLease
    ): ExecutionClaimResult {
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= 200)
        val proposal = store.proposal(proposalId) ?: return ExecutionClaimResult.NotFound
        if (proposal.parametersSha256 != expectedProposalSha256) return ExecutionClaimResult.HashMismatch
        val now = clock.now()
        if (!proposal.expiresAt.isAfter(now)) return ExecutionClaimResult.ProposalExpired
        return store.claimApprovedExecution(proposalId, expectedProposalSha256, idempotencyKey, lease, now)
    }

    override suspend fun beginExecution(claim: ExecutionClaim): Boolean = store.beginClaimedExecution(claim, clock.now())

    override suspend fun recordExecutionOutcome(claim: ExecutionClaim, outcome: ExecutionOutcome, receipt: ByteArray?): Boolean {
        require(receipt == null || receipt.size <= MAX_RECEIPT_BYTES)
        return store.markExecutionOutcome(claim, outcome.name, receipt?.copyOf(), clock.now())
    }

    private fun requiresStrongConfirmation(proposal: ActionProposal): Boolean =
        proposal.impactClass in STRONG_CONFIRMATION_IMPACT ||
            proposal.reversibility in setOf(Reversibility.IRREVERSIBLE, Reversibility.UNKNOWN)

    companion object {
        const val HIFZ_INTERVAL_ACTION = HifzIntervalGrantPolicy.ACTION_TYPE
        const val HIFZ_GRANT_POLICY_VERSION = HifzIntervalGrantPolicy.VERSION
        const val DEFAULT_APPROVAL_LIFETIME_MILLIS = 5 * 60 * 1000L
        const val MAX_APPROVAL_LIFETIME_MILLIS = AuthorizationPolicyLimits.MAX_APPROVAL_LIFETIME_MILLIS
        const val MIN_GRANT_LIFETIME_MILLIS = HifzIntervalGrantPolicy.MIN_LIFETIME_MILLIS
        const val MAX_GRANT_LIFETIME_MILLIS = HifzIntervalGrantPolicy.MAX_LIFETIME_MILLIS
        const val MAX_GRANT_DELTA_MINUTES = HifzIntervalGrantPolicy.MAX_ABSOLUTE_DELTA_MINUTES
        private const val MAX_PARAMETER_BYTES = 64 * 1024
        private const val MAX_RECEIPT_BYTES = 64 * 1024
        private val STRONG_CONFIRMATION_IMPACT = setOf(
            ImpactClass.HIGH, ImpactClass.SENSITIVE, ImpactClass.EXTERNAL,
            ImpactClass.FINANCIAL, ImpactClass.DESTRUCTIVE, ImpactClass.IRREVERSIBLE
        )
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

