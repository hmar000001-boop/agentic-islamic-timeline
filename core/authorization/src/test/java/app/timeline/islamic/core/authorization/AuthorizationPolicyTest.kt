package app.timeline.islamic.core.authorization

import app.timeline.islamic.core.agent.*
import app.timeline.islamic.core.common.Clock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorizationPolicyTest {
    private val now = Instant.parse("2026-11-06T17:00:00Z")
    private val policy = AuthorizationPolicy(Clock { now })

    private fun proposal(
        impact: ImpactClass = ImpactClass.LOW,
        created: Instant = now,
        expires: Instant = now.plusSeconds(60)
    ) = ActionProposal("p-1", "t-1", "timeline.create", "item-1", "a".repeat(64), created, expires, impact, Reversibility.REVERSIBLE)

    @Test fun mutationWithoutApprovalAsksEveryTime() {
        assertEquals(AuthorizationMode.ASK_EACH_TIME, policy.evaluate(proposal()).mode)
    }

    @Test fun expiredProposalIsDeniedEvenWhenApprovalFlagIsTrue() {
        assertEquals(AuthorizationMode.DENY, policy.evaluate(proposal(created = now.minusSeconds(60), expires = now.minusSeconds(1)), true).mode)
    }

    @Test fun highImpactCannotUsePersistentAllowMode() {
        assertEquals(AuthorizationMode.REQUIRE_STRONG_CONFIRMATION, policy.evaluate(proposal(ImpactClass.HIGH), true).mode)
    }

    @Test fun eligibleLowImpactExactApprovalIsScoped() {
        assertEquals(AuthorizationMode.ALLOW_SCOPED, policy.evaluate(proposal(), true).mode)
    }
}
