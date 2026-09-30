package com.devflow.api.sandbox.executor;

import java.util.Objects;

/**
 * Controlled file payload transmitted to the isolated sandbox executor.
 * Untrusted repository code or arbitrary host paths are never transmitted directly.
 */
public record ExecutionFilePayload(
        String filePath,
        String operation,
        String content,
        String diffContent
) {
    public ExecutionFilePayload {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
    }
}
