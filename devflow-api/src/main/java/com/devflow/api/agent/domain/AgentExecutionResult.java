package com.devflow.api.agent.domain;

import java.util.UUID;

public record AgentExecutionResult(UUID executionId, AgentState state, String response, int iterations, int toolCalls) {
}
