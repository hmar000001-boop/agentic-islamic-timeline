package app.timeline.islamic.core.agent.runtime

import app.timeline.islamic.core.agent.AgentTaskStateMachine
import app.timeline.islamic.core.agent.AgentTaskStatus
import org.junit.Assert.*
import org.junit.Test

class AgentTaskStateMachineTest {
    @Test fun runnableCanOnlyStartOrCancel() {
        assertTrue(AgentTaskStateMachine.canTransition(AgentTaskStatus.RUNNABLE, AgentTaskStatus.RUNNING))
        assertTrue(AgentTaskStateMachine.canTransition(AgentTaskStatus.RUNNABLE, AgentTaskStatus.CANCELLED))
        assertFalse(AgentTaskStateMachine.canTransition(AgentTaskStatus.RUNNABLE, AgentTaskStatus.COMPLETED))
        assertFalse(AgentTaskStateMachine.canTransition(AgentTaskStatus.RUNNABLE, AgentTaskStatus.WAITING_FOR_APPROVAL))
    }

    @Test fun waitingForApprovalCannotExecuteWithoutRequeue() {
        assertTrue(AgentTaskStateMachine.canTransition(AgentTaskStatus.WAITING_FOR_APPROVAL, AgentTaskStatus.RUNNABLE))
        assertFalse(AgentTaskStateMachine.canTransition(AgentTaskStatus.WAITING_FOR_APPROVAL, AgentTaskStatus.RUNNING))
        assertFalse(AgentTaskStateMachine.canTransition(AgentTaskStatus.WAITING_FOR_APPROVAL, AgentTaskStatus.COMPLETED))
    }

    @Test fun terminalCompletionCannotBeReopened() {
        assertFalse(AgentTaskStateMachine.canTransition(AgentTaskStatus.COMPLETED, AgentTaskStatus.RUNNABLE))
        assertFalse(AgentTaskStateMachine.canTransition(AgentTaskStatus.COMPLETED, AgentTaskStatus.CANCELLED))
    }

    @Test fun interruptedTaskMustBeRequeuedBeforeLeaseAcquisition() {
        assertTrue(AgentTaskStateMachine.canTransition(AgentTaskStatus.INTERRUPTED, AgentTaskStatus.RUNNABLE))
        assertFalse(AgentTaskStateMachine.canTransition(AgentTaskStatus.INTERRUPTED, AgentTaskStatus.RUNNING))
    }

    @Test fun cancelledTaskCannotBeResumed() {
        assertFalse(AgentTaskStateMachine.canTransition(AgentTaskStatus.CANCELLED, AgentTaskStatus.RUNNABLE))
        assertFalse(AgentTaskStateMachine.canTransition(AgentTaskStatus.CANCELLED, AgentTaskStatus.RUNNING))
    }
}
