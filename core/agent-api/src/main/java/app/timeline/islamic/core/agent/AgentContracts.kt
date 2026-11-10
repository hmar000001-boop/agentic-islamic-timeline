package app.timeline.islamic.core.agent

import java.time.Instant

enum class AgentTaskStatus { RUNNABLE, RUNNING, WAITING_FOR_INPUT, WAITING_FOR_APPROVAL, INTERRUPTED, COMPLETED, FAILED, CANCELLED }
enum class ImpactClass { LOW, MEDIUM, HIGH, SENSITIVE, EXTERNAL, FINANCIAL, DESTRUCTIVE, IRREVERSIBLE }
enum class Reversibility { REVERSIBLE, COMPENSATABLE, IRREVERSIBLE, UNKNOWN }
enum class AuthorizationMode { ASK_EACH_TIME, ALLOW_SCOPED, DENY, REQUIRE_STRONG_CONFIRMATION }
enum class AuthorizationReason { NO_APPROVAL, EXPIRED, SENSITIVE_ACTION, UNKNOWN_ACTION, INVALID_PROPOSAL, APPROVED, GRANT_MATCH }

data class ActionProposal(
    val proposalId: String,
    val taskId: String,
    val actionType: String,
    val targetId: String?,
    val parametersSha256: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val impactClass: ImpactClass,
    val reversibility: Reversibility
) {
    init {
        require(proposalId.isNotBlank())
        require(taskId.isNotBlank())
        require(actionType.isNotBlank())
        require(parametersSha256.matches(Regex("[0-9a-f]{64}")))
        require(expiresAt.isAfter(createdAt))
    }
}

data class AuthorizationDecision(val mode: AuthorizationMode, val reasons: Set<AuthorizationReason>)
