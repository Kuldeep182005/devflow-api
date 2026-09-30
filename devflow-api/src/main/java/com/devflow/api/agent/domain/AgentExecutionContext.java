package com.devflow.api.agent.domain;

import java.util.UUID;

public record AgentExecutionContext(UUID userId, UUID organizationId, UUID taskId, UUID repositoryId,
        String objective, String repositoryName) {
}
