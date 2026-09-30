package com.devflow.api.sandbox;

import com.devflow.api.async.persistence.AsyncJobStore;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.persistence.proposedchange.ProposedChangeRepository;
import com.devflow.api.persistence.sandbox.SandboxExecutionRepository;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeStatus;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.sandbox.config.SandboxProperties;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.sandbox.exception.SandboxException;
import com.devflow.api.sandbox.executor.SandboxExecutionResult;
import com.devflow.api.sandbox.executor.SandboxExecutor;
import com.devflow.api.sandbox.service.SandboxExecutionService;
import com.devflow.api.sandbox.workspace.SandboxWorkspaceManager;
import com.devflow.api.task.domain.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SandboxExecutionServiceTest {

    private SandboxExecutionRepository executionRepository;
    private ProposedChangeRepository proposedChangeRepository;
    private SandboxWorkspaceManager workspaceManager;
    private SandboxExecutor sandboxExecutor;
    private AsyncJobStore asyncJobStore;
    private SandboxProperties properties;
    private SandboxExecutionService service;

    private UUID organizationId;
    private UUID proposedChangeId;

    @BeforeEach
    void setUp() {
        executionRepository = mock(SandboxExecutionRepository.class);
        proposedChangeRepository = mock(ProposedChangeRepository.class);
        workspaceManager = mock(SandboxWorkspaceManager.class);
        sandboxExecutor = mock(SandboxExecutor.class);
        asyncJobStore = mock(AsyncJobStore.class);
        properties = new SandboxProperties();
        properties.setAllowedProfiles(Set.of(SandboxExecutionProfile.JAVA_MAVEN_TEST));
        properties.validate();

        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        ObjectProvider workspaceResolverProvider = mock(ObjectProvider.class);

        service = new SandboxExecutionService(
                executionRepository,
                proposedChangeRepository,
                workspaceManager,
                sandboxExecutor,
                asyncJobStore,
                properties,
                workspaceResolverProvider,
                txManager
        );

        organizationId = UUID.randomUUID();
        proposedChangeId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Rejects queueing when proposed change is not found in tenant")
    void rejectsMissingProposedChange() {
        when(proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.queueExecution(proposedChangeId, organizationId, SandboxExecutionProfile.JAVA_MAVEN_TEST))
                .isInstanceOf(SandboxException.class)
                .hasFieldOrPropertyWithValue("code", "PROPOSED_CHANGE_NOT_FOUND");
    }

    @Test
    @DisplayName("Rejects queueing when proposed change is not validated")
    void rejectsNonValidatedProposal() {
        ProposedChange change = mock(ProposedChange.class);
        when(change.getStatus()).thenReturn(ProposedChangeStatus.DRAFT);
        when(proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId))
                .thenReturn(Optional.of(change));

        assertThatThrownBy(() -> service.queueExecution(proposedChangeId, organizationId, SandboxExecutionProfile.JAVA_MAVEN_TEST))
                .isInstanceOf(SandboxException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PROPOSED_CHANGE_STATUS");
    }

    @Test
    @DisplayName("Rejects queueing when repository is inactive")
    void rejectsInactiveRepository() {
        ProposedChange change = mock(ProposedChange.class);
        when(change.getStatus()).thenReturn(ProposedChangeStatus.VALIDATED);

        Repository repository = mock(Repository.class);
        when(repository.isActive()).thenReturn(false);
        when(change.getRepository()).thenReturn(repository);

        when(proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId))
                .thenReturn(Optional.of(change));

        assertThatThrownBy(() -> service.queueExecution(proposedChangeId, organizationId, SandboxExecutionProfile.JAVA_MAVEN_TEST))
                .isInstanceOf(SandboxException.class)
                .hasFieldOrPropertyWithValue("code", "REPOSITORY_INACTIVE");
    }

    @Test
    @DisplayName("Queues execution and enqueues async job when valid")
    void queuesExecutionSuccessfully() {
        ProposedChange change = mock(ProposedChange.class);
        when(change.getStatus()).thenReturn(ProposedChangeStatus.VALIDATED);

        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(organizationId);

        Repository repo = mock(Repository.class);
        when(repo.isActive()).thenReturn(true);
        when(repo.getOrganization()).thenReturn(org);
        when(repo.getId()).thenReturn(UUID.randomUUID());

        Task task = mock(Task.class);
        when(task.getOrganization()).thenReturn(org);
        when(task.getId()).thenReturn(UUID.randomUUID());

        when(change.getOrganization()).thenReturn(org);
        when(change.getRepository()).thenReturn(repo);
        when(change.getTask()).thenReturn(task);

        when(proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId))
                .thenReturn(Optional.of(change));

        when(executionRepository.save(any(SandboxExecution.class))).thenAnswer(invocation -> {
            SandboxExecution se = invocation.getArgument(0);
            se.assignIdForTesting(UUID.randomUUID());
            return se;
        });

        SandboxExecution queued = service.queueExecution(proposedChangeId, organizationId, SandboxExecutionProfile.JAVA_MAVEN_TEST);

        assertThat(queued).isNotNull();
        assertThat(queued.getStatus()).isEqualTo(SandboxExecutionStatus.QUEUED);
        verify(asyncJobStore).enqueueSandboxExecution(queued.getId());
    }

    @Test
    @DisplayName("Duplicate execution cannot run twice concurrently")
    void duplicateExecutionCannotRunTwice() {
        UUID executionId = UUID.randomUUID();
        SandboxExecution execution = mock(SandboxExecution.class);
        // Already running
        when(execution.getStatus()).thenReturn(SandboxExecutionStatus.RUNNING);
        when(executionRepository.findByIdWithDetails(executionId)).thenReturn(Optional.of(execution));

        SandboxExecution result = service.executeExecution(executionId);

        assertThat(result).isSameAs(execution);
        verify(sandboxExecutor, never()).execute(any(), any());
    }

    @Test
    @DisplayName("Execution transitions QUEUED -> RUNNING -> SUCCEEDED")
    void executeExecutionTransitionsCleanly() {
        Organization org = mock(Organization.class);

        when(org.getId()).thenReturn(organizationId);

        Repository repo = mock(Repository.class);
        when(repo.getId()).thenReturn(UUID.randomUUID());

        Task task = mock(Task.class);
        when(task.getId()).thenReturn(UUID.randomUUID());

        ProposedChange change = mock(ProposedChange.class);
        when(change.getId()).thenReturn(proposedChangeId);

        UUID executionId = UUID.randomUUID();
        SandboxExecution execution = new SandboxExecution(
                change, task, org, repo, SandboxExecutionProfile.JAVA_MAVEN_TEST, Instant.now()
        );
        execution.assignIdForTesting(executionId);



        when(executionRepository.findByIdWithDetails(executionId)).thenReturn(Optional.of(execution));
        when(executionRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(executionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Path mockWorkspace = Path.of("target/mock-workspace");
        when(workspaceManager.prepareWorkspace(any(), any(), any())).thenReturn(mockWorkspace);

        SandboxExecutionResult executorResult = SandboxExecutionResult.succeeded(
                executionId, proposedChangeId, 0, Duration.ofSeconds(2), "build success", "", false
        );
        when(sandboxExecutor.execute(any(), any())).thenReturn(executorResult);

        SandboxExecution completed = service.executeExecution(executionId);

        assertThat(completed.getStatus()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(completed.getExitCode()).isEqualTo(0);
        assertThat(completed.getStdout()).isEqualTo("build success");
        verify(workspaceManager).cleanupWorkspace(executionId);
    }
}
