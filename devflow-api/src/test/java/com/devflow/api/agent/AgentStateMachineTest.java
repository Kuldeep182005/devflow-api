package com.devflow.api.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.devflow.api.agent.domain.AgentState;
import com.devflow.api.agent.exception.AgentExecutionException;
import com.devflow.api.agent.validation.AgentStateMachine;
import org.junit.jupiter.api.Test;

class AgentStateMachineTest {
    private final AgentStateMachine machine = new AgentStateMachine();

    @Test
    void allowsExpectedExecutionTransitions() {
        assertDoesNotThrow(() -> machine.require(AgentState.NEW, AgentState.RUNNING));
        assertDoesNotThrow(() -> machine.require(AgentState.WAITING_FOR_LLM, AgentState.SUCCEEDED));
        assertDoesNotThrow(() -> machine.require(AgentState.WAITING_FOR_TOOL, AgentState.WAITING_FOR_LLM));
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        assertThrows(AgentExecutionException.class,
                () -> machine.require(AgentState.SUCCEEDED, AgentState.RUNNING));
        assertThrows(AgentExecutionException.class,
                () -> machine.require(AgentState.CANCELLED, AgentState.FAILED));
    }
}
