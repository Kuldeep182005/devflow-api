package com.devflow.api.sandbox.executor;

import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable trusted request containing only server-derived identifiers, profile, and controlled file payloads.
 * Callers (such as LLMs or HTTP clients) cannot supply arbitrary commands, paths, or settings.
 */
public record SandboxExecutionRequest(
        UUID executionId,
        UUID proposedChangeId,
        UUID taskId,
        UUID organizationId,
        UUID repositoryId,
        SandboxExecutionProfile profile,
        List<ExecutionFilePayload> files
) {
    public SandboxExecutionRequest(
            UUID executionId,
            UUID proposedChangeId,
            UUID taskId,
            UUID organizationId,
            UUID repositoryId,
            SandboxExecutionProfile profile
    ) {
        this(executionId, proposedChangeId, taskId, organizationId, repositoryId, profile, List.of());
    }

    public SandboxExecutionRequest {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(proposedChangeId, "proposedChangeId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        files = files == null ? List.of() : List.copyOf(files);
    }
}
