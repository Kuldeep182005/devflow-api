package com.devflow.api.github.mutation.service;

import com.devflow.api.approval.domain.Approval;
import com.devflow.api.approval.domain.ApprovalStatus;
import com.devflow.api.async.persistence.AsyncJobStore;
import com.devflow.api.github.mutation.api.GithubMutationResponse;
import com.devflow.api.github.mutation.domain.GithubMutation;
import com.devflow.api.github.mutation.domain.GithubMutationStatus;
import com.devflow.api.github.mutation.exception.GithubMutationException;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.persistence.approval.ApprovalRepository;
import com.devflow.api.persistence.githubmutation.GithubMutationRepository;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeStatus;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskSourceType;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.user.domain.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubMutationServiceTest {

    @Mock
    private GithubMutationRepository githubMutationRepository;

    @Mock
    private ApprovalRepository approvalRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private AsyncJobStore asyncJobStore;

    private GithubMutationService service;

    private UUID userId;
    private UUID orgId;
    private UUID approvalId;
    private Organization org;
    private User user;
    private Repository repository;
    private Task task;
    private ProposedChange proposedChange;
    private SandboxExecution sandboxExecution;
    private Approval approval;

    @BeforeEach
    void setUp() {
        service = new GithubMutationService(
                githubMutationRepository,
                approvalRepository,
                memberRepository,
                asyncJobStore
        );

        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        approvalId = UUID.randomUUID();
        Instant now = Instant.now();

        user = new User("user@example.com", "User", "auth0", true, now, now);
        org = new Organization("Org", "org", now, now);
        repository = new Repository(org, RepositoryProvider.GITHUB, "repo-1", "octocat/hello-world", "main", true, now, now);
        repository.associateGithubInstallation(12345L);

        task = new Task(org, repository, user, TaskSourceType.MANUAL, null, "Task", "Desc",
                TaskStatus.WAITING_FOR_APPROVAL, null, null, null, now, now, null, null);

        proposedChange = new ProposedChange(task, org, repository, ProposedChangeStatus.VALIDATED,
                "Title", "Desc", "base-sha-123", "patch", "{}", now);

        sandboxExecution = new SandboxExecution(proposedChange, task, org, repository,
                SandboxExecutionProfile.JAVA_MAVEN_TEST, now);
        sandboxExecution.markRunning(now);
        sandboxExecution.complete(SandboxExecutionStatus.SUCCEEDED, 0, "SUCCESS", "", false, null, null, now);

        approval = new Approval(proposedChange, task, org, repository, proposedChange.getVersion(),
                sandboxExecution, user, now);
        approval.approve(user, "Approved", now);
    }

    @Test
    @DisplayName("enqueueExecution requires authenticated user")
    void enqueueExecutionRequiresAuth() {
        assertThatThrownBy(() -> service.enqueueExecution(null, orgId, approvalId))
                .isInstanceOf(GithubMutationException.class)
                .satisfies(e -> {
                    GithubMutationException ex = (GithubMutationException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("UNAUTHORIZED");
                });
    }

    @Test
    @DisplayName("enqueueExecution throws 404 if user is not a member of organization")
    void enqueueExecutionThrowsNotFoundForNonMember() {
        when(memberRepository.findByIdOrganizationIdAndIdUserId(orgId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enqueueExecution(userId, orgId, approvalId))
                .isInstanceOf(GithubMutationException.class)
                .satisfies(e -> {
                    GithubMutationException ex = (GithubMutationException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getCode()).isEqualTo("ORGANIZATION_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("enqueueExecution throws 403 Forbidden for MEMBER and VIEWER roles")
    void enqueueExecutionThrowsForbiddenForMemberAndViewer() {
        OrganizationMember member = new OrganizationMember(org, user, OrganizationRole.MEMBER, Instant.now());
        when(memberRepository.findByIdOrganizationIdAndIdUserId(orgId, userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.enqueueExecution(userId, orgId, approvalId))
                .isInstanceOf(GithubMutationException.class)
                .satisfies(e -> {
                    GithubMutationException ex = (GithubMutationException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("FORBIDDEN");
                });

        OrganizationMember viewer = new OrganizationMember(org, user, OrganizationRole.VIEWER, Instant.now());
        when(memberRepository.findByIdOrganizationIdAndIdUserId(orgId, userId)).thenReturn(Optional.of(viewer));

        assertThatThrownBy(() -> service.enqueueExecution(userId, orgId, approvalId))
                .isInstanceOf(GithubMutationException.class)
                .satisfies(e -> {
                    GithubMutationException ex = (GithubMutationException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("FORBIDDEN");
                });
    }

    @Test
    @DisplayName("enqueueExecution throws 404 if approval not found in organization")
    void enqueueExecutionThrowsNotFoundForMissingApproval() {
        OrganizationMember admin = new OrganizationMember(org, user, OrganizationRole.ADMIN, Instant.now());
        when(memberRepository.findByIdOrganizationIdAndIdUserId(orgId, userId)).thenReturn(Optional.of(admin));
        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enqueueExecution(userId, orgId, approvalId))
                .isInstanceOf(GithubMutationException.class)
                .satisfies(e -> {
                    GithubMutationException ex = (GithubMutationException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getCode()).isEqualTo("APPROVAL_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("enqueueExecution throws 409 if approval is not in APPROVED state")
    void enqueueExecutionThrowsConflictForNonApprovedApproval() {
        OrganizationMember admin = new OrganizationMember(org, user, OrganizationRole.ADMIN, Instant.now());
        when(memberRepository.findByIdOrganizationIdAndIdUserId(orgId, userId)).thenReturn(Optional.of(admin));

        Approval pendingApproval = new Approval(proposedChange, task, org, repository,
                proposedChange.getVersion(), sandboxExecution, user, Instant.now());
        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, orgId)).thenReturn(Optional.of(pendingApproval));

        assertThatThrownBy(() -> service.enqueueExecution(userId, orgId, approvalId))
                .isInstanceOf(GithubMutationException.class)
                .satisfies(e -> {
                    GithubMutationException ex = (GithubMutationException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("APPROVAL_NOT_APPROVED");
                });
    }

    @Test
    @DisplayName("enqueueExecution throws 409 if proposed change version does not match approval")
    void enqueueExecutionThrowsConflictForVersionMismatch() {
        OrganizationMember admin = new OrganizationMember(org, user, OrganizationRole.ADMIN, Instant.now());
        when(memberRepository.findByIdOrganizationIdAndIdUserId(orgId, userId)).thenReturn(Optional.of(admin));

        // Create approval pointing to version 999 instead of 0
        Approval staleApproval = new Approval(proposedChange, task, org, repository,
                999L, sandboxExecution, user, Instant.now());
        staleApproval.approve(user, "Approved old", Instant.now());
        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, orgId)).thenReturn(Optional.of(staleApproval));

        assertThatThrownBy(() -> service.enqueueExecution(userId, orgId, approvalId))
                .isInstanceOf(GithubMutationException.class)
                .satisfies(e -> {
                    GithubMutationException ex = (GithubMutationException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("PROPOSAL_VERSION_MISMATCH");
                });
    }

    @Test
    @DisplayName("enqueueExecution throws 409 if sandbox execution is not SUCCEEDED")
    void enqueueExecutionThrowsConflictForNonSuccessfulSandbox() {
        OrganizationMember admin = new OrganizationMember(org, user, OrganizationRole.ADMIN, Instant.now());
        when(memberRepository.findByIdOrganizationIdAndIdUserId(orgId, userId)).thenReturn(Optional.of(admin));

        SandboxExecution failedExecution = new SandboxExecution(proposedChange, task, org, repository,
                SandboxExecutionProfile.JAVA_MAVEN_TEST, Instant.now());
        failedExecution.markRunning(Instant.now());
        failedExecution.complete(SandboxExecutionStatus.FAILED, 1, "", "FAILED", false, "TEST_FAILURE", "Failed", Instant.now());

        Approval approvalWithFailedSandbox = new Approval(proposedChange, task, org, repository,
                proposedChange.getVersion(), failedExecution, user, Instant.now());
        approvalWithFailedSandbox.approve(user, "Approved", Instant.now());

        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, orgId)).thenReturn(Optional.of(approvalWithFailedSandbox));

        assertThatThrownBy(() -> service.enqueueExecution(userId, orgId, approvalId))
                .isInstanceOf(GithubMutationException.class)
                .satisfies(e -> {
                    GithubMutationException ex = (GithubMutationException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("SANDBOX_NOT_SUCCESSFUL");
                });
    }

    @Test
    @DisplayName("enqueueExecution throws 409 if repository has no GitHub installation ID")
    void enqueueExecutionThrowsConflictForMissingInstallation() {
        OrganizationMember admin = new OrganizationMember(org, user, OrganizationRole.ADMIN, Instant.now());
        when(memberRepository.findByIdOrganizationIdAndIdUserId(orgId, userId)).thenReturn(Optional.of(admin));

        Repository repoNoInstallation = new Repository(org, RepositoryProvider.GITHUB, "repo-2", "octocat/no-install", "main", true, Instant.now(), Instant.now());
        Task task2 = new Task(org, repoNoInstallation, user, TaskSourceType.MANUAL, null, "Task2", "Desc",
                TaskStatus.WAITING_FOR_APPROVAL, null, null, null, Instant.now(), Instant.now(), null, null);
        ProposedChange prop2 = new ProposedChange(task2, org, repoNoInstallation, ProposedChangeStatus.VALIDATED,
                "Title", "Desc", "base", "patch", "{}", Instant.now());
        Approval app2 = new Approval(prop2, task2, org, repoNoInstallation, prop2.getVersion(), sandboxExecution, user, Instant.now());
        app2.approve(user, "Approved", Instant.now());

        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, orgId)).thenReturn(Optional.of(app2));

        assertThatThrownBy(() -> service.enqueueExecution(userId, orgId, approvalId))
                .isInstanceOf(GithubMutationException.class)
                .satisfies(e -> {
                    GithubMutationException ex = (GithubMutationException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("GITHUB_INSTALLATION_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("enqueueExecution succeeds: saves QUEUED mutation and enqueues Redis job")
    void enqueueExecutionSucceeds() {
        OrganizationMember owner = new OrganizationMember(org, user, OrganizationRole.OWNER, Instant.now());
        when(memberRepository.findByIdOrganizationIdAndIdUserId(orgId, userId)).thenReturn(Optional.of(owner));
        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, orgId)).thenReturn(Optional.of(approval));
        when(githubMutationRepository.findByOrganizationIdAndRepositoryIdAndProposedChangeIdAndProposedChangeVersion(
                orgId, repository.getId(), proposedChange.getId(), proposedChange.getVersion()))
                .thenReturn(Optional.empty());

        when(githubMutationRepository.saveAndFlush(any(GithubMutation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GithubMutationResponse response = service.enqueueExecution(userId, orgId, approvalId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.proposedChangeVersion()).isEqualTo(proposedChange.getVersion());
        assertThat(response.baseRevision()).isEqualTo("base-sha-123");
        assertThat(response.branchName()).contains("devflow/task-" + task.getId());

        ArgumentCaptor<GithubMutation> captor = ArgumentCaptor.forClass(GithubMutation.class);
        verify(githubMutationRepository).saveAndFlush(captor.capture());
        GithubMutation saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(GithubMutationStatus.QUEUED);

        verify(asyncJobStore).enqueueGithubPrCreation(saved.getId());
    }

    @Test
    @DisplayName("enqueueExecution is idempotent: returns existing mutation without re-enqueuing if already active")
    void enqueueExecutionIdempotent() {
        OrganizationMember admin = new OrganizationMember(org, user, OrganizationRole.ADMIN, Instant.now());
        when(memberRepository.findByIdOrganizationIdAndIdUserId(orgId, userId)).thenReturn(Optional.of(admin));
        when(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, orgId)).thenReturn(Optional.of(approval));

        GithubMutation existingMutation = new GithubMutation(
                UUID.randomUUID(), org, repository, task, proposedChange, proposedChange.getVersion(),
                approval, sandboxExecution, "base-sha-123", "devflow/task-1/change-1", Instant.now()
        );
        existingMutation.markRunning(Instant.now());

        when(githubMutationRepository.findByOrganizationIdAndRepositoryIdAndProposedChangeIdAndProposedChangeVersion(
                orgId, repository.getId(), proposedChange.getId(), proposedChange.getVersion()))
                .thenReturn(Optional.of(existingMutation));

        GithubMutationResponse response = service.enqueueExecution(userId, orgId, approvalId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(existingMutation.getId());
        assertThat(response.status()).isEqualTo("RUNNING");

        verifyNoInteractions(asyncJobStore);
    }
}
