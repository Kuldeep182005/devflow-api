package com.devflow.api.agent.validation;

import com.devflow.api.agent.domain.AgentState;
import com.devflow.api.agent.exception.AgentExecutionException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AgentStateMachine {
    private final Map<AgentState, Set<AgentState>> transitions = Map.of(
            AgentState.NEW, EnumSet.of(AgentState.RUNNING),
            AgentState.RUNNING, EnumSet.of(AgentState.WAITING_FOR_LLM, AgentState.CANCELLED, AgentState.LIMIT_REACHED, AgentState.FAILED),
            AgentState.WAITING_FOR_LLM, EnumSet.of(AgentState.WAITING_FOR_TOOL, AgentState.SUCCEEDED,
                    AgentState.FAILED, AgentState.CANCELLED, AgentState.LIMIT_REACHED),
            AgentState.WAITING_FOR_TOOL, EnumSet.of(AgentState.WAITING_FOR_LLM, AgentState.FAILED,
                    AgentState.CANCELLED, AgentState.LIMIT_REACHED),
            AgentState.SUCCEEDED, Set.of(), AgentState.FAILED, Set.of(),
            AgentState.CANCELLED, Set.of(), AgentState.LIMIT_REACHED, Set.of());

    public void require(AgentState from, AgentState to) {
        if (!transitions.getOrDefault(from, Set.of()).contains(to)) {
            throw new AgentExecutionException("Illegal agent state transition");
        }
    }
}
