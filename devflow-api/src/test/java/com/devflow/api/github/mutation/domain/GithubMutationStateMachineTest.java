package com.devflow.api.github.mutation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GithubMutationStateMachineTest {

    @Test
    @DisplayName("Valid forward state machine transitions succeed")
    void validTransitionsSucceed() {
        assertThat(GithubMutationStatus.QUEUED.canTransitionTo(GithubMutationStatus.RUNNING)).isTrue();
        assertThat(GithubMutationStatus.QUEUED.canTransitionTo(GithubMutationStatus.FAILED)).isTrue();

        assertThat(GithubMutationStatus.RUNNING.canTransitionTo(GithubMutationStatus.COMMITTED)).isTrue();
        assertThat(GithubMutationStatus.RUNNING.canTransitionTo(GithubMutationStatus.BRANCH_CREATED)).isTrue();
        assertThat(GithubMutationStatus.RUNNING.canTransitionTo(GithubMutationStatus.FAILED)).isTrue();

        assertThat(GithubMutationStatus.COMMITTED.canTransitionTo(GithubMutationStatus.BRANCH_CREATED)).isTrue();
        assertThat(GithubMutationStatus.COMMITTED.canTransitionTo(GithubMutationStatus.PR_CREATED)).isTrue();
        assertThat(GithubMutationStatus.COMMITTED.canTransitionTo(GithubMutationStatus.FAILED)).isTrue();

        assertThat(GithubMutationStatus.BRANCH_CREATED.canTransitionTo(GithubMutationStatus.PR_CREATED)).isTrue();
        assertThat(GithubMutationStatus.BRANCH_CREATED.canTransitionTo(GithubMutationStatus.FAILED)).isTrue();

        assertThat(GithubMutationStatus.PR_CREATED.canTransitionTo(GithubMutationStatus.SUCCEEDED)).isTrue();
        assertThat(GithubMutationStatus.PR_CREATED.canTransitionTo(GithubMutationStatus.FAILED)).isTrue();
    }

    @Test
    @DisplayName("Self transition is always allowed")
    void selfTransitionAllowed() {
        for (GithubMutationStatus status : GithubMutationStatus.values()) {
            assertThat(status.canTransitionTo(status)).isTrue();
        }
    }

    @Test
    @DisplayName("SUCCEEDED is a terminal state and cannot transition anywhere")
    void succeededIsTerminal() {
        assertThat(GithubMutationStatus.SUCCEEDED.isTerminal()).isTrue();
        for (GithubMutationStatus status : GithubMutationStatus.values()) {
            if (status != GithubMutationStatus.SUCCEEDED) {
                assertThat(GithubMutationStatus.SUCCEEDED.canTransitionTo(status)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("FAILED status can be reset to QUEUED or RUNNING for retry/reconciliation")
    void failedCanRetry() {
        assertThat(GithubMutationStatus.FAILED.canTransitionTo(GithubMutationStatus.QUEUED)).isTrue();
        assertThat(GithubMutationStatus.FAILED.canTransitionTo(GithubMutationStatus.RUNNING)).isTrue();
        assertThat(GithubMutationStatus.FAILED.canTransitionTo(GithubMutationStatus.SUCCEEDED)).isFalse();
    }

    @Test
    @DisplayName("Null transition targets return false safely")
    void nullTargetReturnsFalse() {
        for (GithubMutationStatus status : GithubMutationStatus.values()) {
            assertThat(status.canTransitionTo(null)).isFalse();
        }
    }
}
