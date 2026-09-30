package com.devflow.api.observability.evaluation;

import com.devflow.api.observability.metrics.DevFlowMetrics;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.task.domain.Task;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AiEvaluationService.class);

    private final AiEvaluationRepository repository;
    private final DevFlowMetrics metrics;
    private final jakarta.persistence.EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired
    public AiEvaluationService(
            AiEvaluationRepository repository,
            org.springframework.beans.factory.ObjectProvider<DevFlowMetrics> metricsProvider,
            jakarta.persistence.EntityManager entityManager) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.metrics = metricsProvider != null ? metricsProvider.getIfAvailable() : null;
        this.entityManager = entityManager;
    }

    public AiEvaluationService(
            AiEvaluationRepository repository,
            DevFlowMetrics metrics,
            jakarta.persistence.EntityManager entityManager) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.metrics = metrics;
        this.entityManager = entityManager;
    }

    public AiEvaluationService(AiEvaluationRepository repository, DevFlowMetrics metrics) {
        this(repository, metrics, null);
    }

    @Transactional
    public AiEvaluation recordExecutionStart(
            UUID organizationId,
            UUID taskId,
            UUID executionId,
            String provider,
            String model) {
        Organization organization = entityManager != null ? entityManager.getReference(Organization.class, organizationId) : null;
        Task task = entityManager != null ? entityManager.getReference(Task.class, taskId) : null;
        return recordExecutionStart(organization, task, executionId, provider, model);
    }

    @Transactional
    public AiEvaluation recordExecutionStart(
            Organization organization,
            Task task,
            UUID executionId,
            String provider,
            String model) {
        AiEvaluation evaluation = new AiEvaluation(
                UUID.randomUUID(),
                organization,
                task,
                executionId,
                provider != null ? provider : "unknown",
                model != null ? model : "default",
                Instant.now(),
                0,
                0,
                "RUNNING");
        return repository.save(evaluation);
    }

    @Transactional
    public void recordExecutionComplete(
            UUID executionId,
            int iterations,
            int toolCalls,
            String finalState,
            String failureCode) {
        Optional<AiEvaluation> optional = repository.findByExecutionId(executionId);
        if (optional.isPresent()) {
            AiEvaluation eval = optional.get();
            eval.complete(Instant.now(), finalState, failureCode);
            repository.save(eval);
        }
        if (metrics != null) {
            metrics.recordAiAgentCompletion(finalState);
        }
    }

    @Transactional
    public void linkProposal(UUID executionId, UUID proposedChangeId) {
        repository.findByExecutionId(executionId).ifPresent(eval -> {
            eval.linkProposal(proposedChangeId);
            repository.save(eval);
        });
        if (metrics != null) {
            metrics.recordAiProposalProduced();
        }
    }

    @Transactional
    public void linkSandbox(UUID executionId, UUID sandboxExecutionId, boolean succeeded) {
        repository.findByExecutionId(executionId).ifPresent(eval -> {
            eval.linkSandbox(sandboxExecutionId);
            repository.save(eval);
        });
        if (metrics != null) {
            if (succeeded) {
                metrics.recordAiSandboxPassed();
            } else {
                metrics.recordAiSandboxFailed();
            }
        }
    }

    @Transactional
    public void linkApproval(UUID executionId, UUID approvalId) {
        repository.findByExecutionId(executionId).ifPresent(eval -> {
            eval.linkApproval(approvalId);
            repository.save(eval);
        });
        if (metrics != null) {
            metrics.recordAiApprovalGranted();
        }
    }

    @Transactional
    public void linkMutation(UUID executionId, UUID mutationId) {
        repository.findByExecutionId(executionId).ifPresent(eval -> {
            eval.linkMutation(mutationId);
            repository.save(eval);
        });
        if (metrics != null) {
            metrics.recordAiPrCreated();
        }
    }
}
