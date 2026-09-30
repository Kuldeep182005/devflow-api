package com.devflow.api.observability.evaluation;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.task.domain.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ai_evaluations")
public class AiEvaluation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "iteration_count", nullable = false)
    private int iterationCount;

    @Column(name = "tool_call_count", nullable = false)
    private int toolCallCount;

    @Column(name = "final_state", nullable = false, length = 32)
    private String finalState;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "proposed_change_id")
    private UUID proposedChangeId;

    @Column(name = "sandbox_execution_id")
    private UUID sandboxExecutionId;

    @Column(name = "approval_id")
    private UUID approvalId;

    @Column(name = "github_mutation_id")
    private UUID githubMutationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected AiEvaluation() {
    }

    public AiEvaluation(
            UUID id,
            Organization organization,
            Task task,
            UUID executionId,
            String provider,
            String model,
            Instant startedAt,
            int iterationCount,
            int toolCallCount,
            String finalState) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.organization = Objects.requireNonNull(organization, "organization must not be null");
        this.task = Objects.requireNonNull(task, "task must not be null");
        this.executionId = Objects.requireNonNull(executionId, "executionId must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.iterationCount = iterationCount;
        this.toolCallCount = toolCallCount;
        this.finalState = Objects.requireNonNull(finalState, "finalState must not be null");
        this.createdAt = Instant.now();
    }

    public void complete(Instant completedAt, String finalState, String failureCode) {
        this.completedAt = completedAt;
        this.finalState = finalState;
        this.failureCode = failureCode;
        if (completedAt != null && startedAt != null) {
            this.durationMs = Math.max(0, java.time.Duration.between(startedAt, completedAt).toMillis());
        }
    }

    public void linkProposal(UUID proposedChangeId) {
        this.proposedChangeId = proposedChangeId;
    }

    public void linkSandbox(UUID sandboxExecutionId) {
        this.sandboxExecutionId = sandboxExecutionId;
    }

    public void linkApproval(UUID approvalId) {
        this.approvalId = approvalId;
    }

    public void linkMutation(UUID githubMutationId) {
        this.githubMutationId = githubMutationId;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Task getTask() {
        return task;
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public String getFinalState() {
        return finalState;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public UUID getProposedChangeId() {
        return proposedChangeId;
    }

    public UUID getSandboxExecutionId() {
        return sandboxExecutionId;
    }

    public UUID getApprovalId() {
        return approvalId;
    }

    public UUID getGithubMutationId() {
        return githubMutationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
