package app.timeline.islamic.core.authorization

import app.timeline.islamic.core.agent.AuthorizationServicePort
import app.timeline.islamic.core.agent.ExecutionClaim
import app.timeline.islamic.core.agent.ExecutionOutcome
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** A handler may report FAILED only when it knows no external side effect was applied. */
fun interface ApprovedToolHandler {
    suspend fun execute(targetId: String?, canonicalParameters: ByteArray, idempotencyKey: String): ToolHandlerResult
}

sealed interface ToolHandlerResult {
    data class Verified(val receipt: ByteArray? = null) : ToolHandlerResult
    data class AppliedUnverified(val receipt: ByteArray? = null) : ToolHandlerResult
    data class OutcomeUnknown(val receipt: ByteArray? = null) : ToolHandlerResult
    data class FailedBeforeEffect(val receipt: ByteArray? = null) : ToolHandlerResult
}

sealed interface ToolGatewayResult {
    data class Finished(val outcome: ExecutionOutcome) : ToolGatewayResult
    data object ClaimRejected : ToolGatewayResult
    data object HandlerUnavailable : ToolGatewayResult
    data object ParameterHashMismatch : ToolGatewayResult
    data object OutcomePersistenceFailed : ToolGatewayResult
}

/**
 * The only path from a persisted execution claim to a registered side-effect handler.
 * It atomically consumes CLAIMED before calling the handler; process death after EXECUTING
 * is reconciled as OUTCOME_UNKNOWN and is never retried automatically.
 */
class ToolExecutionGateway(
    private val authorization: AuthorizationServicePort,
    private val handlers: Map<String, ApprovedToolHandler>
) {
    suspend fun execute(claim: ExecutionClaim, canonicalParameters: ByteArray): ToolGatewayResult {
        if (sha256(canonicalParameters) != claim.proposalSha256) return ToolGatewayResult.ParameterHashMismatch
        val handler = handlers[claim.actionType] ?: return ToolGatewayResult.HandlerUnavailable
        if (!authorization.beginExecution(claim)) return ToolGatewayResult.ClaimRejected

        return try {
            val result = handler.execute(claim.targetId, canonicalParameters.copyOf(), claim.idempotencyKey)
            val outcome = when (result) {
                is ToolHandlerResult.Verified -> ExecutionOutcome.VERIFIED
                is ToolHandlerResult.AppliedUnverified -> ExecutionOutcome.APPLIED_UNVERIFIED
                is ToolHandlerResult.OutcomeUnknown -> ExecutionOutcome.OUTCOME_UNKNOWN
                is ToolHandlerResult.FailedBeforeEffect -> ExecutionOutcome.FAILED
            }
            val receipt = when (result) {
                is ToolHandlerResult.Verified -> result.receipt
                is ToolHandlerResult.AppliedUnverified -> result.receipt
                is ToolHandlerResult.OutcomeUnknown -> result.receipt
                is ToolHandlerResult.FailedBeforeEffect -> result.receipt
            }
            if (!authorization.recordExecutionOutcome(claim, outcome, receipt)) {
                ToolGatewayResult.OutcomePersistenceFailed
            } else ToolGatewayResult.Finished(outcome)
        } catch (cancelled: CancellationException) {
            // The current Job is already cancelled. Persist uncertainty in a NonCancellable
            // section; if storage still fails, startup reconciliation sees EXECUTING and marks
            // it OUTCOME_UNKNOWN without replaying the external effect.
            withContext(NonCancellable) {
                runCatching { authorization.recordExecutionOutcome(claim, ExecutionOutcome.OUTCOME_UNKNOWN) }
            }
            throw cancelled
        } catch (_: Exception) {
            val recorded = authorization.recordExecutionOutcome(claim, ExecutionOutcome.OUTCOME_UNKNOWN)
            if (recorded) ToolGatewayResult.Finished(ExecutionOutcome.OUTCOME_UNKNOWN)
            else ToolGatewayResult.OutcomePersistenceFailed
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
