package com.devflow.api.approval.service;

import com.devflow.api.approval.api.ApproveApprovalRequest;
import com.devflow.api.approval.api.RejectApprovalRequest;
import com.devflow.api.approval.api.RequestApprovalRequest;
import com.devflow.api.approval.domain.Approval;
import com.devflow.api.approval.domain.ApprovalStatus;
import com.devflow.api.approval.exception.ApprovalException;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.persistence.approval.ApprovalRepository;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.proposedchange.ProposedChangeRepository;
import com.devflow.api.persistence.sandbox.SandboxExecutionRepository;
import com.devflow.api.persistence.task.TaskEventRepository;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeStatus;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.task.domain.TaskEventType;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.user.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalServiceTest {

    private ApprovalRepository approvalRepository;
    private ProposedChangeRepository proposedChangeRepository;
    private SandboxExecutionRepository sandboxExecutionRepository;
    private OrganizationMemberRepository memberRepository;
    private TaskEventRepository taskEventRepository;
    private ObjectMapper objectMapper;
    private ApprovalService service;

    private UUID organizationId;
    private UUID userId;
    private UUID proposedChangeId;
    private UUID approvalId;

    @BeforeEach
    void setUp() {
        approvalRepository = mock(ApprovalRepository.class);
        proposedChangeRepository = mock(ProposedChangeRepository.class);
        sandboxExecutionRepository = mock(SandboxExecutionRepository.class);
        memberRepository = mock(OrganizationMemberRepository.class);
        taskEventRepository = mock(TaskEventRepository.class);
        objectMapper = new ObjectMapper();

        service = new ApprovalService(
                approvalRepository,
                proposedChangeRepository,
                sandboxExecutionRepository,
                memberRepository,
                taskEventRepository,
                objectMapper
        );

        organizationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        proposedChangeId = UUID.randomUUID();
        approvalId = UUID.randomUUID();
    }

    private OrganizationMember setupMember(OrganizationRole role) {
        OrganizationMember member = mock(OrganizationMember.class);
        Organization org = mock(Organization.class);
        User user = mock(User.class);
        when(org.getId()).thenReturn(organizationId);
        when(user.getId()).thenReturn(userId);
        when(member.getOrganization()).thenReturn(org);
        when(member.getUser()).thenReturn(user);
        when(member.getRole()).thenReturn(role);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));
        return member;
    }

    // =========================================================================
    // requestApproval tests
    // =========================================================================

    @Test
    @DisplayName("requestApproval rejects unauthenticated caller")
    void requestApprovalRejectsUnauthenticated() {
        assertThatThrownBy(() -> service.requestApproval(null, organizationId, new RequestApprovalRequest(proposedChangeId)))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "UNAUTHORIZED");
    }

    @Test
    @DisplayName("requestApproval rejects non-member with 404 without leaking existence")
    void requestApprovalRejectsNonMember() {
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestApproval(userId, organizationId, new RequestApprovalRequest(proposedChangeId)))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "ORGANIZATION_NOT_FOUND");
    }

    @Test
    @DisplayName("requestApproval rejects VIEWER role with 403")
    void requestApprovalRejectsViewerRole() {
        setupMember(OrganizationRole.VIEWER);

        assertThatThrownBy(() -> service.requestApproval(userId, organizationId, new RequestApprovalRequest(proposedChangeId)))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    @DisplayName("requestApproval rejects missing proposed change with 404")
    void requestApprovalRejectsMissingProposedChange() {
        setupMember(OrganizationRole.MEMBER);
        when(proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestApproval(userId, organizationId, new RequestApprovalRequest(proposedChangeId)))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "PROPOSED_CHANGE_NOT_FOUND");
    }

    @Test
    @DisplayName("requestApproval rejects DRAFT proposed change with 409")
    void requestApprovalRejectsDraftProposedChange() {
        setupMember(OrganizationRole.MEMBER);

        ProposedChange pc = mock(ProposedChange.class);
        when(pc.getStatus()).thenReturn(ProposedChangeStatus.DRAFT);
        when(proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId))
                .thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> service.requestApproval(userId, organizationId, new RequestApprovalRequest(proposedChangeId)))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PROPOSED_CHANGE_STATUS");
    }

    @Test
    @DisplayName("requestApproval rejects inactive repository with 409")
    void requestApprovalRejectsInactiveRepository() {
        setupMember(OrganizationRole.MEMBER);

        ProposedChange pc = mock(ProposedChange.class);
        when(pc.getStatus()).thenReturn(ProposedChangeStatus.VALIDATED);
        Repository repo = mock(Repository.class);
        when(repo.isActive()).thenReturn(false);
        when(pc.getRepository()).thenReturn(repo);

        when(proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId))
                .thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> service.requestApproval(userId, organizationId, new RequestApprovalRequest(proposedChangeId)))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "REPOSITORY_INACTIVE");
    }

    @Test
    @DisplayName("requestApproval rejects when active pending approval already exists with 409")
    void requestApprovalRejectsDuplicatePendingApproval() {
        setupMember(OrganizationRole.MEMBER);

        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(organizationId);

        ProposedChange pc = mock(ProposedChange.class);
        when(pc.getId()).thenReturn(proposedChangeId);
        when(pc.getStatus()).thenReturn(ProposedChangeStatus.VALIDATED);
        Repository repo = mock(Repository.class);
        when(repo.isActive()).thenReturn(true);
        when(repo.getOrganization()).thenReturn(org);
        when(pc.getRepository()).thenReturn(repo);

        when(proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId))
                .thenReturn(Optional.of(pc));
        when(approvalRepository.existsByProposedChangeIdAndStatus(proposedChangeId, ApprovalStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.requestApproval(userId, organizationId, new RequestApprovalRequest(proposedChangeId)))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "ACTIVE_APPROVAL_EXISTS");
    }

    @Test
    @DisplayName("requestApproval rejects when no sandbox execution exists with 409")
    void requestApprovalRejectsMissingSandboxExecution() {
        setupMember(OrganizationRole.MEMBER);

        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(organizationId);

        ProposedChange pc = mock(ProposedChange.class);
        when(pc.getId()).thenReturn(proposedChangeId);
        when(pc.getStatus()).thenReturn(ProposedChangeStatus.VALIDATED);
        Repository repo = mock(Repository.class);
        when(repo.isActive()).thenReturn(true);
        when(repo.getOrganization()).thenReturn(org);
        when(pc.getRepository()).thenReturn(repo);

        when(proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId))
                .thenReturn(Optional.of(pc));
        when(approvalRepository.existsByProposedChangeIdAndStatus(proposedChangeId, ApprovalStatus.PENDING))
                .thenReturn(false);
        when(sandboxExecutionRepository.findByProposedChangeIdOrderByCreatedAtDesc(proposedChangeId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.requestApproval(userId, organizationId, new RequestApprovalRequest(proposedChangeId)))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "NO_SUCCESSFUL_SANDBOX_EXECUTION");
    }

    @Test
    @DisplayName("requestApproval rejects when latest sandbox execution is FAILED with 409")
    void requestApprovalRejectsFailedSandboxExecution() {
        setupMember(OrganizationRole.MEMBER);

        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(organizationId);

        ProposedChange pc = mock(ProposedChange.class);
        when(pc.getId()).thenReturn(proposedChangeId);
        when(pc.getStatus()).thenReturn(ProposedChangeStatus.VALIDATED);
        Repository repo = mock(Repository.class);
        when(repo.isActive()).thenReturn(true);
        when(repo.getOrganization()).thenReturn(org);
        when(pc.getRepository()).thenReturn(repo);

        when(proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId))
                .thenReturn(Optional.of(pc));
        when(approvalRepository.existsByProposedChangeIdAndStatus(proposedChangeId, ApprovalStatus.PENDING))
                .thenReturn(false);

        SandboxExecution failedExecution = mock(SandboxExecution.class);
        when(failedExecution.getStatus()).thenReturn(SandboxExecutionStatus.FAILED);
        when(sandboxExecutionRepository.findByProposedChangeIdOrderByCreatedAtDesc(proposedChangeId))
                .thenReturn(List.of(failedExecution));

        assertThatThrownBy(() -> service.requestApproval(userId, organizationId, new RequestApprovalRequest(proposedChangeId)))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "NO_SUCCESSFUL_SANDBOX_EXECUTION");
    }

    @Test
    @DisplayName("requestApproval rejects when sandbox execution is for older version with 409 STALE_SANDBOX_EXECUTION")
    void requestApprovalRejectsStaleSandboxExecution() {
        setupMember(OrganizationRole.MEMBER);

        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(organizationId);

        ProposedChange pc = mock(ProposedChange.class);
        when(pc.getId()).thenReturn(proposedChangeId);
        when(pc.getStatus()).thenReturn(ProposedChangeStatus.VALIDATED);
        when(pc.getVersion()).thenReturn(2L); // proposal is at version 2
        Repository repo = mock(Repository.class);
        when(repo.isActive()).thenReturn(true);
        when(repo.getOrganization()).thenReturn(org);
        when(pc.getRepository()).thenReturn(repo);

        when(proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId))
                .thenReturn(Optional.of(pc));
        when(approvalRepository.existsByProposedChangeIdAndStatus(proposedChangeId, ApprovalStatus.PENDING))
                .thenReturn(false);

        SandboxExecution oldExecution = mock(SandboxExecution.class);
        when(oldExecution.getStatus()).thenReturn(SandboxExecutionStatus.SUCCEEDED);
        when(oldExecution.getProposedChangeVersion()).thenReturn(1L); // executed against version 1
        when(sandboxExecutionRepository.findByProposedChangeIdOrderByCreatedAtDesc(proposedChangeId))
                .thenReturn(List.of(oldExecution));

        assertThatThrownBy(() -> service.requestApproval(userId, organizationId, new RequestApprovalRequest(proposedChangeId)))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "STALE_SANDBOX_EXECUTION");
    }

    @Test
    @DisplayName("requestApproval succeeds and persists APPROVAL_REQUESTED TaskEvent")
    void requestApprovalSucceeds() {
        setupMember(OrganizationRole.MEMBER);

        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(organizationId);

        Repository repo = mock(Repository.class);
        when(repo.getId()).thenReturn(UUID.randomUUID());
        when(repo.isActive()).thenReturn(true);
        when(repo.getOrganization()).thenReturn(org);

        Task task = mock(Task.class);
        when(task.getId()).thenReturn(UUID.randomUUID());
        when(task.getStatus()).thenReturn(TaskStatus.WAITING_FOR_APPROVAL);

        ProposedChange pc = mock(ProposedChange.class);
        when(pc.getId()).thenReturn(proposedChangeId);
        when(pc.getStatus()).thenReturn(ProposedChangeStatus.VALIDATED);
        when(pc.getVersion()).thenReturn(1L);
        when(pc.getRepository()).thenReturn(repo);
        when(pc.getOrganization()).thenReturn(org);
        when(pc.getTask()).thenReturn(task);

        when(proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId))
                .thenReturn(Optional.of(pc));
        when(approvalRepository.existsByProposedChangeIdAndStatus(proposedChangeId, ApprovalStatus.PENDING))
                .thenReturn(false);

        SandboxExecution execution = mock(SandboxExecution.class);
        when(execution.getId()).thenReturn(UUID.randomUUID());
        when(execution.getStatus()).thenReturn(SandboxExecutionStatus.SUCCEEDED);
        when(execution.getProposedChangeVersion()).thenReturn(1L);
        when(execution.getOrganization()).thenReturn(org);
        when(execution.getTask()).thenReturn(task);
        when(execution.getRepository()).thenReturn(repo);

        when(sandboxExecutionRepository.findByProposedChangeIdOrderByCreatedAtDesc(proposedChangeId))
                .thenReturn(List.of(execution));

        when(approvalRepository.saveAndFlush(any(Approval.class))).thenAnswer(i -> {
            Approval a = i.getArgument(0);
            a.assignIdForTesting(approvalId);
            return a;
        });

        Approval result = service.requestApproval(userId, organizationId, new RequestApprovalRequest(proposedChangeId));

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(result.getProposedChangeVersion()).isEqualTo(1L);
        verify(approvalRepository).saveAndFlush(any(Approval.class));
        verify(taskEventRepository).save(any(TaskEvent.class));
    }

    // =========================================================================
    // approve tests
    // =========================================================================

    @Test
    @DisplayName("approve rejects MEMBER role with 403 FORBIDDEN")
    void approveRejectsMemberRole() {
        setupMember(OrganizationRole.MEMBER);

        assertThatThrownBy(() -> service.approve(userId, organizationId, approvalId, new ApproveApprovalRequest("LGTM")))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    @DisplayName("approve rejects VIEWER role with 403 FORBIDDEN")
    void approveRejectsViewerRole() {
        setupMember(OrganizationRole.VIEWER);

        assertThatThrownBy(() -> service.approve(userId, organizationId, approvalId, new ApproveApprovalRequest("LGTM")))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    @DisplayName("approve succeeds for OWNER role")
    void approveSucceedsForOwnerRole() {
        setupMember(OrganizationRole.OWNER);

        Approval approval = buildMockApproval(1L, 1L, SandboxExecutionStatus.SUCCEEDED, ProposedChangeStatus.VALIDATED, true);
        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId))
                .thenReturn(Optional.of(approval));
        when(approvalRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        Approval result = service.approve(userId, organizationId, approvalId, new ApproveApprovalRequest("LGTM"));

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.getDecisionReason()).isEqualTo("LGTM");
        verify(taskEventRepository).save(any(TaskEvent.class));
    }

    @Test
    @DisplayName("approve succeeds for ADMIN role")
    void approveSucceedsForAdminRole() {
        setupMember(OrganizationRole.ADMIN);

        Approval approval = buildMockApproval(1L, 1L, SandboxExecutionStatus.SUCCEEDED, ProposedChangeStatus.VALIDATED, true);
        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId))
                .thenReturn(Optional.of(approval));
        when(approvalRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        Approval result = service.approve(userId, organizationId, approvalId, new ApproveApprovalRequest("Approved by Admin"));

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        verify(taskEventRepository).save(any(TaskEvent.class));
    }

    @Test
    @DisplayName("approve is idempotent when repeated by the same decider")
    void approveIsIdempotentForSameDecider() {
        OrganizationMember member = setupMember(OrganizationRole.ADMIN);

        Approval approval = buildMockApproval(1L, 1L, SandboxExecutionStatus.SUCCEEDED, ProposedChangeStatus.VALIDATED, true);
        approval.approve(member.getUser(), "Initial approval", Instant.now());

        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId))
                .thenReturn(Optional.of(approval));

        Approval result = service.approve(userId, organizationId, approvalId, new ApproveApprovalRequest("Retry"));

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        // Does not re-save or re-emit event on idempotent return
        verify(approvalRepository, never()).saveAndFlush(any());
        verify(taskEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve fails with 409 when proposal was modified after approval was requested (stale version)")
    void approveFailsWhenProposalModified() {
        setupMember(OrganizationRole.ADMIN);

        // Approval requested for version 1, but proposal is now at version 2
        Approval approval = buildMockApproval(1L, 2L, SandboxExecutionStatus.SUCCEEDED, ProposedChangeStatus.VALIDATED, true);
        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId))
                .thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.approve(userId, organizationId, approvalId, new ApproveApprovalRequest("LGTM")))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "PROPOSED_CHANGE_STALE");
    }

    @Test
    @DisplayName("approve fails with 409 when already rejected")
    void approveFailsWhenAlreadyRejected() {
        OrganizationMember member = setupMember(OrganizationRole.ADMIN);

        Approval approval = buildMockApproval(1L, 1L, SandboxExecutionStatus.SUCCEEDED, ProposedChangeStatus.VALIDATED, true);
        approval.reject(member.getUser(), "Rejected earlier", Instant.now());

        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId))
                .thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.approve(userId, organizationId, approvalId, new ApproveApprovalRequest("LGTM")))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "APPROVAL_ALREADY_DECIDED");
    }

    @Test
    @DisplayName("approve maps OptimisticLockingFailureException to 409 conflict")
    void approveMapsOptimisticLockConflict() {
        setupMember(OrganizationRole.ADMIN);

        Approval approval = buildMockApproval(1L, 1L, SandboxExecutionStatus.SUCCEEDED, ProposedChangeStatus.VALIDATED, true);
        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId))
                .thenReturn(Optional.of(approval));
        when(approvalRepository.saveAndFlush(any()))
                .thenThrow(new OptimisticLockingFailureException("Concurrent update"));

        assertThatThrownBy(() -> service.approve(userId, organizationId, approvalId, new ApproveApprovalRequest("LGTM")))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "APPROVAL_TRANSITION_CONFLICT");
    }

    // =========================================================================
    // reject tests
    // =========================================================================

    @Test
    @DisplayName("reject rejects MEMBER role with 403 FORBIDDEN")
    void rejectRejectsMemberRole() {
        setupMember(OrganizationRole.MEMBER);

        assertThatThrownBy(() -> service.reject(userId, organizationId, approvalId, new RejectApprovalRequest("Security flaw")))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    @DisplayName("reject validates reason not blank")
    void rejectValidatesReasonNotBlank() {
        setupMember(OrganizationRole.OWNER);

        assertThatThrownBy(() -> service.reject(userId, organizationId, approvalId, new RejectApprovalRequest("   ")))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_APPROVAL_REASON");
    }

    @Test
    @DisplayName("reject validates illegal control characters in reason")
    void rejectValidatesControlCharacters() {
        setupMember(OrganizationRole.OWNER);

        assertThatThrownBy(() -> service.reject(userId, organizationId, approvalId, new RejectApprovalRequest("Bad\u0000Reason")))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_APPROVAL_REASON");
    }

    @Test
    @DisplayName("reject succeeds for OWNER role")
    void rejectSucceedsForOwnerRole() {
        setupMember(OrganizationRole.OWNER);

        Approval approval = buildMockApproval(1L, 1L, SandboxExecutionStatus.SUCCEEDED, ProposedChangeStatus.VALIDATED, true);
        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId))
                .thenReturn(Optional.of(approval));
        when(approvalRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        Approval result = service.reject(userId, organizationId, approvalId, new RejectApprovalRequest("Buggy logic"));

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(result.getDecisionReason()).isEqualTo("Buggy logic");
        verify(taskEventRepository).save(any(TaskEvent.class));
    }

    @Test
    @DisplayName("reject fails with 409 when already approved")
    void rejectFailsWhenAlreadyApproved() {
        OrganizationMember member = setupMember(OrganizationRole.ADMIN);

        Approval approval = buildMockApproval(1L, 1L, SandboxExecutionStatus.SUCCEEDED, ProposedChangeStatus.VALIDATED, true);
        approval.approve(member.getUser(), "Approved earlier", Instant.now());

        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId))
                .thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.reject(userId, organizationId, approvalId, new RejectApprovalRequest("Now trying to reject")))
                .isInstanceOf(ApprovalException.class)
                .hasFieldOrPropertyWithValue("code", "APPROVAL_ALREADY_DECIDED");
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private Approval buildMockApproval(
            long approvalPcVersion,
            long currentPcVersion,
            SandboxExecutionStatus executionStatus,
            ProposedChangeStatus pcStatus,
            boolean repoActive) {

        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(organizationId);

        Repository repo = mock(Repository.class);
        when(repo.getId()).thenReturn(UUID.randomUUID());
        when(repo.isActive()).thenReturn(repoActive);
        when(repo.getOrganization()).thenReturn(org);

        Task task = mock(Task.class);
        when(task.getId()).thenReturn(UUID.randomUUID());
        when(task.getStatus()).thenReturn(TaskStatus.WAITING_FOR_APPROVAL);

        ProposedChange pc = mock(ProposedChange.class);
        when(pc.getId()).thenReturn(proposedChangeId);
        when(pc.getStatus()).thenReturn(pcStatus);
        when(pc.getVersion()).thenReturn(currentPcVersion);
        when(pc.getRepository()).thenReturn(repo);
        when(pc.getOrganization()).thenReturn(org);
        when(pc.getTask()).thenReturn(task);

        SandboxExecution execution = mock(SandboxExecution.class);
        when(execution.getId()).thenReturn(UUID.randomUUID());
        when(execution.getStatus()).thenReturn(executionStatus);
        when(execution.getProposedChangeVersion()).thenReturn(approvalPcVersion);
        when(execution.getOrganization()).thenReturn(org);
        when(execution.getTask()).thenReturn(task);
        when(execution.getRepository()).thenReturn(repo);

        User requester = mock(User.class);
        when(requester.getId()).thenReturn(UUID.randomUUID());

        Approval approval = new Approval(
                pc, task, org, repo, approvalPcVersion, execution, requester, Instant.now()
        );
        approval.assignIdForTesting(approvalId);
        return approval;
    }
}
