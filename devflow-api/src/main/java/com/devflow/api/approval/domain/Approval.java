package com.devflow.api.approval.domain;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.task.domain.Task;
import com.devflow.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "approvals")
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposed_change_id", nullable = false)
    private ProposedChange proposedChange;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status;

    @Column(name = "proposed_change_version", nullable = false)
    private long proposedChangeVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sandbox_execution_id", nullable = false)
    private SandboxExecution sandboxExecution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_user_id")
    private User decidedBy;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Approval() {
    }

    public Approval(
            ProposedChange proposedChange,
            Task task,
            Organization organization,
            Repository repository,
            long proposedChangeVersion,
            SandboxExecution sandboxExecution,
            User requestedBy,
            Instant now) {
        this.proposedChange = Objects.requireNonNull(proposedChange, "proposedChange must not be null");
        this.task = Objects.requireNonNull(task, "task must not be null");
        this.organization = Objects.requireNonNull(organization, "organization must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.proposedChangeVersion = proposedChangeVersion;
        this.sandboxExecution = Objects.requireNonNull(sandboxExecution, "sandboxExecution must not be null");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        this.status = ApprovalStatus.PENDING;
        this.requestedAt = Objects.requireNonNull(now, "now must not be null");
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void assignIdForTesting(UUID id) {
        this.id = id;
    }

    public boolean approve(User decider, String comment, Instant now) {
        Objects.requireNonNull(decider, "decider must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!this.status.canTransitionTo(ApprovalStatus.APPROVED)) {
            return false;
        }
        this.status = ApprovalStatus.APPROVED;
        this.decidedBy = decider;
        this.decisionReason = safeSubstring(comment, 1000);
        this.decidedAt = now;
        this.updatedAt = now;
        return true;
    }

    public boolean reject(User decider, String reason, Instant now) {
        Objects.requireNonNull(decider, "decider must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!this.status.canTransitionTo(ApprovalStatus.REJECTED)) {
            return false;
        }
        this.status = ApprovalStatus.REJECTED;
        this.decidedBy = decider;
        this.decisionReason = safeSubstring(reason, 1000);
        this.decidedAt = now;
        this.updatedAt = now;
        return true;
    }

    public boolean cancel(User canceller, String reason, Instant now) {
        Objects.requireNonNull(canceller, "canceller must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!this.status.canTransitionTo(ApprovalStatus.CANCELLED)) {
            return false;
        }
        this.status = ApprovalStatus.CANCELLED;
        this.decidedBy = canceller;
        this.decisionReason = safeSubstring(reason, 1000);
        this.decidedAt = now;
        this.updatedAt = now;
        return true;
    }

    private static String safeSubstring(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    public UUID getId() { return id; }
    public ProposedChange getProposedChange() { return proposedChange; }
    public Task getTask() { return task; }
    public Organization getOrganization() { return organization; }
    public Repository getRepository() { return repository; }
    public ApprovalStatus getStatus() { return status; }
    public long getProposedChangeVersion() { return proposedChangeVersion; }
    public SandboxExecution getSandboxExecution() { return sandboxExecution; }
    public User getRequestedBy() { return requestedBy; }
    public User getDecidedBy() { return decidedBy; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
