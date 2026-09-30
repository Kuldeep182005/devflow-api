package com.devflow.api.sandbox.executor;

import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed result containing only safe metadata and bounded outputs.
 * Never includes host paths, credentials, or internal secrets.
 */
public record SandboxExecutionResult(
        UUID executionId,
        UUID proposedChangeId,
        SandboxExecutionStatus status,
        Integer exitCode,
        Duration duration,
        String stdout,
        String stderr,
        boolean outputTruncated,
        String failureCode,
        String failureMessage
) {
    public SandboxExecutionResult {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(proposedChangeId, "proposedChangeId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (duration == null) {
            duration = Duration.ZERO;
        }
    }

    public static SandboxExecutionResult succeeded(
            UUID executionId, UUID proposedChangeId, int exitCode, Duration duration,
            String stdout, String stderr, boolean outputTruncated) {
        return new SandboxExecutionResult(
                executionId, proposedChangeId, SandboxExecutionStatus.SUCCEEDED,
                exitCode, duration, stdout, stderr, outputTruncated, null, null);
    }

    public static SandboxExecutionResult failed(
            UUID executionId, UUID proposedChangeId, Integer exitCode, Duration duration,
            String stdout, String stderr, boolean outputTruncated, String failureCode, String failureMessage) {
        return new SandboxExecutionResult(
                executionId, proposedChangeId, SandboxExecutionStatus.FAILED,
                exitCode, duration, stdout, stderr, outputTruncated, failureCode, failureMessage);
    }

    public static SandboxExecutionResult timedOut(
            UUID executionId, UUID proposedChangeId, Duration duration,
            String stdout, String stderr, boolean outputTruncated) {
        return new SandboxExecutionResult(
                executionId, proposedChangeId, SandboxExecutionStatus.TIMED_OUT,
                null, duration, stdout, stderr, outputTruncated, "EXECUTION_TIMEOUT", "Execution exceeded time limit");
    }

    public static SandboxExecutionResult cancelled(
            UUID executionId, UUID proposedChangeId, Duration duration) {
        return new SandboxExecutionResult(
                executionId, proposedChangeId, SandboxExecutionStatus.CANCELLED,
                null, duration, null, null, false, "EXECUTION_CANCELLED", "Execution was cancelled by server");
    }

    public static SandboxExecutionResult resourceLimit(
            UUID executionId, UUID proposedChangeId, Duration duration, String reason) {
        return new SandboxExecutionResult(
                executionId, proposedChangeId, SandboxExecutionStatus.RESOURCE_LIMIT,
                null, duration, null, null, false, "RESOURCE_LIMIT_EXCEEDED", reason);
    }

    public static SandboxExecutionResult executorUnavailable(
            UUID executionId, UUID proposedChangeId, String message) {
        return new SandboxExecutionResult(
                executionId, proposedChangeId, SandboxExecutionStatus.EXECUTOR_UNAVAILABLE,
                null, Duration.ZERO, null, null, false, "EXECUTOR_UNAVAILABLE", message);
    }

    public static SandboxExecutionResult invalidRequest(
            UUID executionId, UUID proposedChangeId, String message) {
        return new SandboxExecutionResult(
                executionId, proposedChangeId, SandboxExecutionStatus.FAILED,
                null, Duration.ZERO, null, null, false, "INVALID_REQUEST", message);
    }
}
