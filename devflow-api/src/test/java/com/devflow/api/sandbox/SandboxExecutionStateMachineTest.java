package com.devflow.api.sandbox;

import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SandboxExecutionStateMachineTest {

    @Test
    @DisplayName("Valid transition from QUEUED to RUNNING")
    void queuedToRunning() {
        assertThat(SandboxExecutionStatus.QUEUED.canTransitionTo(SandboxExecutionStatus.RUNNING)).isTrue();
    }

    @Test
    @DisplayName("Valid transition from QUEUED to CANCELLED")
    void queuedToCancelled() {
        assertThat(SandboxExecutionStatus.QUEUED.canTransitionTo(SandboxExecutionStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("Invalid transition from QUEUED directly to SUCCEEDED")
    void queuedToSucceededInvalid() {
        assertThat(SandboxExecutionStatus.QUEUED.canTransitionTo(SandboxExecutionStatus.SUCCEEDED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = SandboxExecutionStatus.class, names = {
            "SUCCEEDED", "FAILED", "TIMED_OUT", "CANCELLED", "RESOURCE_LIMIT", "EXECUTOR_UNAVAILABLE"
    })
    @DisplayName("Valid transitions from RUNNING to any terminal state")
    void runningToTerminalStates(SandboxExecutionStatus terminalStatus) {
        assertThat(terminalStatus.isTerminal()).isTrue();
        assertThat(SandboxExecutionStatus.RUNNING.canTransitionTo(terminalStatus)).isTrue();
    }

    @Test
    @DisplayName("Invalid transition from RUNNING to QUEUED")
    void runningToQueuedInvalid() {
        assertThat(SandboxExecutionStatus.RUNNING.canTransitionTo(SandboxExecutionStatus.QUEUED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = SandboxExecutionStatus.class, names = {
            "SUCCEEDED", "FAILED", "TIMED_OUT", "CANCELLED", "RESOURCE_LIMIT", "EXECUTOR_UNAVAILABLE"
    })
    @DisplayName("Terminal states cannot transition to any other state")
    void terminalStatesCannotTransition(SandboxExecutionStatus terminalStatus) {
        for (SandboxExecutionStatus target : SandboxExecutionStatus.values()) {
            assertThat(terminalStatus.canTransitionTo(target)).isFalse();
        }
    }
}
