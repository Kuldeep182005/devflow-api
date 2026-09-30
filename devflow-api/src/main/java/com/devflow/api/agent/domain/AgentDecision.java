package com.devflow.api.agent.domain;

public record AgentDecision(
        AgentAction action,
        String reasoningSummary,
        AgentToolRequest toolRequest,
        String finalResponse) {
}
