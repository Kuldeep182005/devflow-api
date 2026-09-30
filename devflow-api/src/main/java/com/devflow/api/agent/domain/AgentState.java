package com.devflow.api.agent.domain;

public enum AgentState {
    NEW,
    RUNNING,
    WAITING_FOR_LLM,
    WAITING_FOR_TOOL,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    LIMIT_REACHED
}
