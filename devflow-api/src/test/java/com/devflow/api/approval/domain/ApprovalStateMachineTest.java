package com.devflow.api.approval.domain;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.task.domain.Task;
import com.devflow.api.user.domain.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApprovalStateMachineTest {

    private ProposedChange proposedChange;
    private Task task;
    private Organization organization;
    private Repository repository;
    private SandboxExecution sandboxExecution;
    private User requester;
    private User decider;

    @BeforeEach
    void setUp() {
        proposedChange = mock(ProposedChange.class);
        task = mock(Task.class);
        organization = mock(Organization.class);
        repository = mock(Repository.class);
        sandboxExecution = mock(SandboxExecution.class);
        requester = mock(User.class);
        decider = mock(User.class);

        when(requester.getId()).thenReturn(UUID.randomUUID());
        when(decider.getId()).thenReturn(UUID.randomUUID());
        when(proposedChange.getVersion()).thenReturn(1L);
    }

    @Test
    @DisplayName("Status transitions follow explicit allowed path")
    void statusTransitionsFollowExplicitAllowedPath() {
        assertThat(ApprovalStatus.PENDING.isTerminal()).isFalse();
        assertThat(ApprovalStatus.PENDING.canTransitionTo(ApprovalStatus.APPROVED)).isTrue();
        assertThat(ApprovalStatus.PENDING.canTransitionTo(ApprovalStatus.REJECTED)).isTrue();
        assertThat(ApprovalStatus.PENDING.canTransitionTo(ApprovalStatus.CANCELLED)).isTrue();
        assertThat(ApprovalStatus.PENDING.canTransitionTo(ApprovalStatus.PENDING)).isFalse();
        assertThat(ApprovalStatus.PENDING.canTransitionTo(null)).isFalse();

        assertThat(ApprovalStatus.APPROVED.isTerminal()).isTrue();
        assertThat(ApprovalStatus.APPROVED.canTransitionTo(ApprovalStatus.REJECTED)).isFalse();
        assertThat(ApprovalStatus.APPROVED.canTransitionTo(ApprovalStatus.PENDING)).isFalse();
        assertThat(ApprovalStatus.APPROVED.canTransitionTo(ApprovalStatus.CANCELLED)).isFalse();
        assertThat(ApprovalStatus.APPROVED.canTransitionTo(ApprovalStatus.APPROVED)).isFalse();

        assertThat(ApprovalStatus.REJECTED.isTerminal()).isTrue();
        assertThat(ApprovalStatus.REJECTED.canTransitionTo(ApprovalStatus.APPROVED)).isFalse();
        assertThat(ApprovalStatus.REJECTED.canTransitionTo(ApprovalStatus.PENDING)).isFalse();
        assertThat(ApprovalStatus.REJECTED.canTransitionTo(ApprovalStatus.CANCELLED)).isFalse();

        assertThat(ApprovalStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(ApprovalStatus.CANCELLED.canTransitionTo(ApprovalStatus.APPROVED)).isFalse();
        assertThat(ApprovalStatus.CANCELLED.canTransitionTo(ApprovalStatus.REJECTED)).isFalse();
    }

    @Test
    @DisplayName("Approval starts in PENDING status")
    void approvalStartsInPending() {
        Instant now = Instant.now();
        Approval approval = new Approval(
                proposedChange, task, organization, repository, 1L, sandboxExecution, requester, now
        );

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approval.getRequestedBy()).isEqualTo(requester);
        assertThat(approval.getDecidedBy()).isNull();
        assertThat(approval.getDecidedAt()).isNull();
        assertThat(approval.getDecisionReason()).isNull();
        assertThat(approval.getProposedChangeVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Transition PENDING -> APPROVED captures decider, reason, timestamp")
    void pendingToApprovedCapturesDeciderAndReason() {
        Instant now = Instant.now();
        Approval approval = new Approval(
                proposedChange, task, organization, repository, 1L, sandboxExecution, requester, now
        );

        Instant decidedAt = now.plusSeconds(30);
        boolean success = approval.approve(decider, "Looks great to merge", decidedAt);

        assertThat(success).isTrue();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getDecidedBy()).isEqualTo(decider);
        assertThat(approval.getDecisionReason()).isEqualTo("Looks great to merge");
        assertThat(approval.getDecidedAt()).isEqualTo(decidedAt);
        assertThat(approval.getUpdatedAt()).isEqualTo(decidedAt);

        // Cannot transition again once approved
        assertThat(approval.approve(decider, "second approve", decidedAt.plusSeconds(1))).isFalse();
        assertThat(approval.reject(decider, "try reject", decidedAt.plusSeconds(1))).isFalse();
        assertThat(approval.cancel(decider, "try cancel", decidedAt.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("Transition PENDING -> REJECTED captures decider, reason, timestamp")
    void pendingToRejectedCapturesDeciderAndReason() {
        Instant now = Instant.now();
        Approval approval = new Approval(
                proposedChange, task, organization, repository, 1L, sandboxExecution, requester, now
        );

        Instant decidedAt = now.plusSeconds(30);
        boolean success = approval.reject(decider, "Security vulnerability found", decidedAt);

        assertThat(success).isTrue();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(approval.getDecidedBy()).isEqualTo(decider);
        assertThat(approval.getDecisionReason()).isEqualTo("Security vulnerability found");
        assertThat(approval.getDecidedAt()).isEqualTo(decidedAt);

        // Cannot transition again once rejected
        assertThat(approval.approve(decider, "try approve", decidedAt.plusSeconds(1))).isFalse();
        assertThat(approval.reject(decider, "second reject", decidedAt.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("Transition PENDING -> CANCELLED captures canceller, reason, timestamp")
    void pendingToCancelledCapturesCancellerAndReason() {
        Instant now = Instant.now();
        Approval approval = new Approval(
                proposedChange, task, organization, repository, 1L, sandboxExecution, requester, now
        );

        Instant decidedAt = now.plusSeconds(30);
        boolean success = approval.cancel(requester, "Superseded by newer change", decidedAt);

        assertThat(success).isTrue();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(approval.getDecidedBy()).isEqualTo(requester);
        assertThat(approval.getDecisionReason()).isEqualTo("Superseded by newer change");
        assertThat(approval.getDecidedAt()).isEqualTo(decidedAt);

        // Cannot transition once cancelled
        assertThat(approval.approve(decider, "try approve", decidedAt.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("Decision reason is safely truncated to 1000 characters")
    void reasonIsSafelyTruncated() {
        Instant now = Instant.now();
        Approval approval = new Approval(
                proposedChange, task, organization, repository, 1L, sandboxExecution, requester, now
        );

        String longComment = "a".repeat(1200);
        approval.approve(decider, longComment, now);

        assertThat(approval.getDecisionReason()).hasSize(1000);
    }
}
