package com.devflow.api.sandbox.domain;

import java.util.Set;

public enum SandboxExecutionStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    RESOURCE_LIMIT,
    EXECUTOR_UNAVAILABLE;

    private static final Set<SandboxExecutionStatus> TERMINAL_STATES = Set.of(
            SUCCEEDED,
            FAILED,
            TIMED_OUT,
            CANCELLED,
            RESOURCE_LIMIT,
            EXECUTOR_UNAVAILABLE
    );

    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }

    public boolean canTransitionTo(SandboxExecutionStatus target) {
        if (target == null) {
            return false;
        }
        if (isTerminal()) {
            return false;
        }
        return switch (this) {
            case QUEUED -> target == RUNNING || target == CANCELLED;
            case RUNNING -> target.isTerminal();
            default -> false;
        };
    }
}
