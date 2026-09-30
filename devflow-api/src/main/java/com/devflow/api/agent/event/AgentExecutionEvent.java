package com.devflow.api.agent.event;

import java.time.Instant;
import java.util.UUID;

public record AgentExecutionEvent(UUID executionId, UUID taskId, AgentEventType type,
        String summary, Instant occurredAt) {
}
