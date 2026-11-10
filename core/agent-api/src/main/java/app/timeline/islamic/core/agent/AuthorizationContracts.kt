package app.timeline.islamic.core.agent

import java.time.Instant


/** Versioned policy constants shared by the authorization engine and persistence adapter. */
object AuthorizationPolicyLimits {
    const val MAX_APPROVAL_LIFETIME_MILLIS = 24L * 60 * 60 * 1000
}

object HifzIntervalGrantPolicy {
    const val ACTION_TYPE = "hifz.adjust_review_interval"
    const val VERSION = "hifz-interval.v1"
    const val MIN_LIFETIME_MILLIS = 60_000L
    const val MAX_LIFETIME_MILLIS = 30L * 24 * 60 * 60 * 1000
    const val MAX_ABSOLUTE_DELTA_MINUTES = 180
}

/** Explicitly typed parameters for the only action allowed to receive a persistent grant in Phase 3. */
data class HifzIntervalChange(
    val planId: String,
    val basePlanRevision: Long,
    val requestedDeltaMinutes: Int,
    val scheduleProfileId: String
) {
    init {
        require(planId.isNotBlank() && scheduleProfileId.isNotBlank())
        require(!planId.contains('\u001f') && !scheduleProfileId.contains('\u001f'))
        require(basePlanRevision > 0)
        require(requestedDeltaMinutes in -1440..1440 && requestedDeltaMinutes != 0)
    }

    fun canonicalBytes(): ByteArray = listOf(
        "hifz.interval.v1", planId, basePlanRevision.toString(),
        requestedDeltaMinutes.toString(), scheduleProfileId
    ).joinToString("\u001f").toByteArray(Charsets.UTF_8)

    fun immutableFingerprint(): String = sha256(listOf(
        "hifz.interval.scope.v1", planId, basePlanRevision.toString(), scheduleProfileId
    ).joinToString("\u001f").toByteArray(Charsets.UTF_8))
}

data class HifzIntervalGrant(
    val grantId: String,
    val planId: String,
    val immutableParametersSha256: String,
    val maxAbsoluteDeltaMinutes: Int,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val policyVersion: String
) {
    init {
        require(grantId.isNotBlank() && planId.isNotBlank())
        require(immutableParametersSha256.matches(Regex("[0-9a-f]{64}")))
        require(maxAbsoluteDeltaMinutes in 1..1440)
        require(expiresAt.isAfter(createdAt))
        require(policyVersion.isNotBlank())
    }

    fun matches(change: HifzIntervalChange, now: Instant): Boolean =
        revokedAt == null && !now.isBefore(createdAt) && now.isBefore(expiresAt) &&
            planId == change.planId &&
            immutableParametersSha256 == change.immutableFingerprint() &&
            kotlin.math.abs(change.requestedDeltaMinutes) <= maxAbsoluteDeltaMinutes
}

enum class ApprovalState { PENDING, APPROVED, REJECTED, EXPIRED, REVOKED }
enum class ConfirmationStrength { STANDARD, STRONG }

data class UserConfirmation(
    val proposalId: String,
    val proposalSha256: String,
    val confirmedAt: Instant,
    val strength: ConfirmationStrength
) {
    init {
        require(proposalId.isNotBlank())
        require(proposalSha256.matches(Regex("[0-9a-f]{64}")))
    }
}

data class ApprovalSnapshot(
    val approvalId: String,
    val proposalId: String,
    val proposalSha256: String,
    val state: ApprovalState,
    val expiresAt: Instant,
    val decisionAt: Instant?,
    val sourceGrantId: String? = null
)

data class ExecutionClaim(
    val executionId: String,
    val proposalId: String,
    val proposalSha256: String,
    val actionType: String,
    val targetId: String?,
    val idempotencyKey: String,
    val taskId: String,
    val leaseOwner: String,
    val fencingToken: Long,
    val claimedAt: Instant
) {
    init {
        require(executionId.isNotBlank() && proposalId.isNotBlank() && actionType.isNotBlank())
        require(proposalSha256.matches(Regex("[0-9a-f]{64}")))
        require(idempotencyKey.isNotBlank() && taskId.isNotBlank() && leaseOwner.isNotBlank())
        require(fencingToken > 0)
    }
}

sealed interface ExecutionClaimResult {
    data class Claimed(val claim: ExecutionClaim) : ExecutionClaimResult
    data object NotFound : ExecutionClaimResult
    data object HashMismatch : ExecutionClaimResult
    data object ApprovalMissing : ExecutionClaimResult
    data object ApprovalExpired : ExecutionClaimResult
    data object TaskNotRunning : ExecutionClaimResult
    data object LeaseMismatch : ExecutionClaimResult
    data object AlreadyClaimed : ExecutionClaimResult
    data object ProposalExpired : ExecutionClaimResult
}

/** Repository methods with transaction semantics documented by their Room implementation. */
interface AuthorizationStore {
    suspend fun persistProposalAndApproval(
        proposal: ActionProposal,
        canonicalParameters: ByteArray,
        approvalId: String,
        lease: TaskLease,
        now: Instant
    ): Boolean
    suspend fun persistProposalUsingHifzGrant(
        proposal: ActionProposal,
        canonicalParameters: ByteArray,
        approvalId: String,
        grantId: String,
        lease: TaskLease,
        now: Instant
    ): Boolean
    suspend fun proposal(proposalId: String): ActionProposal?
    suspend fun approveExact(confirmation: UserConfirmation, approvalExpiresAt: Instant): Boolean
    suspend fun rejectExact(proposalId: String, proposalSha256: String, at: Instant): Boolean
    suspend fun createHifzGrant(grant: HifzIntervalGrant): Boolean
    suspend fun revokeGrant(grantId: String, at: Instant): Boolean
    suspend fun matchingHifzGrant(change: HifzIntervalChange, now: Instant): HifzIntervalGrant?
    suspend fun beginClaimedExecution(claim: ExecutionClaim, now: Instant): Boolean
    suspend fun claimApprovedExecution(
        proposalId: String,
        expectedProposalSha256: String,
        idempotencyKey: String,
        lease: TaskLease,
        now: Instant
    ): ExecutionClaimResult
    suspend fun markExecutionOutcome(claim: ExecutionClaim, terminalState: String, receipt: ByteArray?, now: Instant): Boolean
    suspend fun reconcileExecutingExecutions(now: Instant): Int
    suspend fun expirePendingApprovals(now: Instant): Int
    suspend fun approvals(proposalId: String): List<ApprovalSnapshot>
}

private fun sha256(value: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(value).joinToString("") { "%02x".format(it) }

sealed interface ActionProposalResult {
    data class NeedsApproval(val proposal: ActionProposal, val approvalId: String) : ActionProposalResult
    data class CoveredByScopedGrant(val proposal: ActionProposal, val grantId: String) : ActionProposalResult
    data object Unavailable : ActionProposalResult
}

interface AuthorizationServicePort {
    suspend fun propose(
        taskId: String, lease: TaskLease, actionType: String, targetId: String?,
        canonicalParameters: ByteArray, impactClass: ImpactClass,
        reversibility: Reversibility, expiresAt: Instant
    ): ActionProposalResult
    suspend fun proposeHifzIntervalChange(taskId: String, lease: TaskLease, change: HifzIntervalChange, expiresAt: Instant): ActionProposalResult
    suspend fun approve(confirmation: UserConfirmation, approvalLifetimeMillis: Long = 300_000L): Boolean
    suspend fun reject(proposalId: String, proposalSha256: String): Boolean
    suspend fun claimExecution(proposalId: String, expectedProposalSha256: String, idempotencyKey: String, lease: TaskLease): ExecutionClaimResult
    suspend fun beginExecution(claim: ExecutionClaim): Boolean
    suspend fun recordExecutionOutcome(claim: ExecutionClaim, outcome: ExecutionOutcome, receipt: ByteArray? = null): Boolean
}

enum class ExecutionOutcome { VERIFIED, FAILED, OUTCOME_UNKNOWN, APPLIED_UNVERIFIED }
