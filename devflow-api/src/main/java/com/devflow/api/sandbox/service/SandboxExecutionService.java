package com.devflow.api.sandbox.service;

import com.devflow.api.async.persistence.AsyncJobStore;
import com.devflow.api.persistence.proposedchange.ProposedChangeRepository;
import com.devflow.api.persistence.sandbox.SandboxExecutionRepository;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeStatus;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repositorytool.domain.RepositoryWorkspace;
import com.devflow.api.repositorytool.workspace.WorkspaceResolver;
import com.devflow.api.sandbox.config.SandboxProperties;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.sandbox.exception.SandboxExceptions;
import com.devflow.api.sandbox.executor.SandboxExecutionRequest;
import com.devflow.api.sandbox.executor.SandboxExecutionResult;
import com.devflow.api.sandbox.executor.SandboxExecutor;
import com.devflow.api.sandbox.workspace.SandboxWorkspaceException;
import com.devflow.api.sandbox.workspace.SandboxWorkspaceManager;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SandboxExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SandboxExecutionService.class);

    private final SandboxExecutionRepository executionRepository;
    private final ProposedChangeRepository proposedChangeRepository;
    private final SandboxWorkspaceManager workspaceManager;
    private final SandboxExecutor sandboxExecutor;
    private final AsyncJobStore asyncJobStore;
    private final SandboxProperties properties;
    private final ObjectProvider<WorkspaceResolver> workspaceResolverProvider;
    private final TransactionTemplate transactionTemplate;

    public SandboxExecutionService(
            SandboxExecutionRepository executionRepository,
            ProposedChangeRepository proposedChangeRepository,
            SandboxWorkspaceManager workspaceManager,
            SandboxExecutor sandboxExecutor,
            AsyncJobStore asyncJobStore,
            SandboxProperties properties,
            ObjectProvider<WorkspaceResolver> workspaceResolverProvider,
            PlatformTransactionManager transactionManager) {
        this.executionRepository = Objects.requireNonNull(executionRepository, "executionRepository must not be null");
        this.proposedChangeRepository = Objects.requireNonNull(proposedChangeRepository, "proposedChangeRepository must not be null");
        this.workspaceManager = Objects.requireNonNull(workspaceManager, "workspaceManager must not be null");
        this.sandboxExecutor = Objects.requireNonNull(sandboxExecutor, "sandboxExecutor must not be null");
        this.asyncJobStore = Objects.requireNonNull(asyncJobStore, "asyncJobStore must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.workspaceResolverProvider = workspaceResolverProvider;
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        properties.validate();
    }

    /**
     * Enqueues an execution for a validated proposed change.
     */
    public SandboxExecution queueExecution(UUID proposedChangeId, UUID organizationId, SandboxExecutionProfile profile) {
        if (!properties.isEnabled()) {
            throw SandboxExceptions.disabled();
        }
        Objects.requireNonNull(proposedChangeId, "proposedChangeId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        if (profile == null || !properties.getAllowedProfiles().contains(profile)) {
            throw SandboxExceptions.invalidProfile(profile == null ? "null" : profile.name());
        }

        return transactionTemplate.execute(status -> {
            ProposedChange proposedChange = proposedChangeRepository.findByIdAndOrganizationId(proposedChangeId, organizationId)
                    .orElseThrow(SandboxExceptions::proposedChangeNotFound);


            if (proposedChange.getStatus() != ProposedChangeStatus.VALIDATED) {
                throw SandboxExceptions.invalidProposalStatus();
            }

            Repository repository = proposedChange.getRepository();
            if (!repository.isActive()) {
                throw SandboxExceptions.inactiveRepository();
            }
            if (!repository.getOrganization().getId().equals(organizationId)) {
                throw SandboxExceptions.forbidden();
            }

            SandboxExecution execution = new SandboxExecution(
                    proposedChange,
                    proposedChange.getTask(),
                    proposedChange.getOrganization(),
                    repository,
                    profile,
                    Instant.now()
            );

            execution = executionRepository.save(execution);
            asyncJobStore.enqueueSandboxExecution(execution.getId());
            return execution;
        });
    }

    /**
     * Executes the queued sandbox execution. Safe for retries and concurrent workers.
     */
    public SandboxExecution executeExecution(UUID executionId) {
        Objects.requireNonNull(executionId, "executionId must not be null");

        // 1. Atomically claim execution by transitioning QUEUED -> RUNNING
        java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean(false);
        SandboxExecution execution = transactionTemplate.execute(status -> {
            Optional<SandboxExecution> optional = executionRepository.findByIdWithDetails(executionId);
            if (optional.isEmpty()) {
                return null;
            }
            SandboxExecution entity = optional.get();
            if (entity.getStatus() != SandboxExecutionStatus.QUEUED) {
                return entity; // Already claimed or terminal
            }
            if (!entity.markRunning(Instant.now())) {
                return entity;
            }
            if (entity.getProposedChange() != null) {
                entity.getProposedChange().getFiles().size();
            }
            claimed.set(true);
            return executionRepository.saveAndFlush(entity);
        });

        if (execution == null || !claimed.get()) {
            return execution;
        }


        // 2. Perform isolated execution outside DB transaction
        SandboxExecutionResult result;
        Path workspaceDir = null;
        try {
            Path baseRepoRoot = null;
            WorkspaceResolver resolver = workspaceResolverProvider.getIfAvailable();
            if (resolver != null) {
                try {
                    RepositoryWorkspace repoWorkspace = resolver.resolve(execution.getRepository().getId());
                    if (repoWorkspace != null) {
                        baseRepoRoot = repoWorkspace.root();
                    }
                } catch (Exception e) {
                    log.debug("Workspace resolver could not locate base repository: {}", e.getMessage());
                }
            }

            workspaceDir = workspaceManager.prepareWorkspace(
                    execution.getId(), baseRepoRoot, execution.getProposedChange());

            SandboxExecutionRequest request = new SandboxExecutionRequest(
                    execution.getId(),
                    execution.getProposedChange().getId(),
                    execution.getTask().getId(),
                    execution.getOrganization().getId(),
                    execution.getRepository().getId(),
                    execution.getProfile()
            );

            result = sandboxExecutor.execute(request, workspaceDir);

        } catch (SandboxWorkspaceException swe) {
            log.warn("Sandbox workspace preparation failed: {} - {}", swe.getFailureCode(), swe.getMessage());
            result = SandboxExecutionResult.failed(
                    execution.getId(), execution.getProposedChange().getId(), null,
                    java.time.Duration.ZERO, null, null, false, swe.getFailureCode(), swe.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during sandbox execution: {}", e.getMessage(), e);
            result = SandboxExecutionResult.executorUnavailable(
                    execution.getId(), execution.getProposedChange().getId(), e.getMessage());
        } finally {
            workspaceManager.cleanupWorkspace(execution.getId());
        }

        // 3. Persist terminal result in DB transaction
        final SandboxExecutionResult finalResult = result;
        return transactionTemplate.execute(status -> {
            SandboxExecution entity = executionRepository.findByIdWithDetails(executionId)
                    .orElseThrow(SandboxExceptions::notFound);

            if (entity.getStatus().isTerminal()) {
                return entity;
            }

            entity.complete(
                    finalResult.status(),
                    finalResult.exitCode(),
                    finalResult.stdout(),
                    finalResult.stderr(),
                    finalResult.outputTruncated(),
                    finalResult.failureCode(),
                    finalResult.failureMessage(),
                    Instant.now()
            );

            return executionRepository.save(entity);
        });
    }

    /**
     * Cancels an ongoing execution, signaling the executor and updating status.
     */
    public SandboxExecution cancelExecution(UUID executionId, UUID organizationId) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");

        sandboxExecutor.cancel(executionId);
        workspaceManager.cleanupWorkspace(executionId);

        return transactionTemplate.execute(status -> {
            SandboxExecution execution = executionRepository.findByIdAndOrganizationId(executionId, organizationId)
                    .orElseThrow(SandboxExceptions::notFound);

            if (execution.getStatus().isTerminal()) {
                return execution;
            }

            execution.markCancelled(Instant.now());
            return executionRepository.save(execution);
        });
    }

    /**
     * Retrieves an execution by ID within organization scope.
     */
    public SandboxExecution getExecution(UUID executionId, UUID organizationId) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");

        return executionRepository.findByIdAndOrganizationId(executionId, organizationId)
                .orElseThrow(SandboxExceptions::notFound);
    }
}
