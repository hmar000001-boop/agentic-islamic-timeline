package app.timeline.islamic.core.authorization

import app.timeline.islamic.core.agent.*
import app.timeline.islamic.core.common.Clock
import app.timeline.islamic.core.common.SystemClock

/** Phase-1 safe baseline: without a persisted, verified approval, every mutation asks each time. */
class AuthorizationPolicy(private val clock: Clock = SystemClock) {
    fun evaluate(proposal: ActionProposal, hasExactOneTimeApproval: Boolean = false): AuthorizationDecision {
        if (!proposal.expiresAt.isAfter(clock.now())) {
            return AuthorizationDecision(AuthorizationMode.DENY, setOf(AuthorizationReason.EXPIRED))
        }
        if (proposal.impactClass in ALWAYS_CONFIRM) {
            return AuthorizationDecision(
                AuthorizationMode.REQUIRE_STRONG_CONFIRMATION,
                setOf(AuthorizationReason.SENSITIVE_ACTION)
            )
        }
        if (hasExactOneTimeApproval) {
            return AuthorizationDecision(AuthorizationMode.ALLOW_SCOPED, setOf(AuthorizationReason.APPROVED))
        }
        return AuthorizationDecision(AuthorizationMode.ASK_EACH_TIME, setOf(AuthorizationReason.NO_APPROVAL))
    }

    private companion object {
        val ALWAYS_CONFIRM = setOf(
            ImpactClass.HIGH, ImpactClass.SENSITIVE, ImpactClass.EXTERNAL,
            ImpactClass.FINANCIAL, ImpactClass.DESTRUCTIVE, ImpactClass.IRREVERSIBLE
        )
    }
}
