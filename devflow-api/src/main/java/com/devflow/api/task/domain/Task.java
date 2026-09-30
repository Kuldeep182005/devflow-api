package com.devflow.api.task.domain;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.repository.domain.Repository;
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
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private TaskSourceType sourceType;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TaskStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "branch_name", length = 255)
    private String branchName;

    @Column(name = "current_step", length = 255)
    private String currentStep;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_message", length = 2000)
    private String failureMessage;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected Task() {
    }

    public Task(
            Organization organization,
            Repository repository,
            User createdBy,
            TaskSourceType sourceType,
            String sourceReference,
            String title,
            String description,
            TaskStatus status,
            String branchName,
            String currentStep,
            String failureReason,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant finishedAt) {
        this(organization, repository, createdBy, sourceType, sourceReference, title, description, status,
                branchName, currentStep, failureReason, createdAt, updatedAt, startedAt, finishedAt,
                null, null, null);
    }

    public Task(
            Organization organization,
            Repository repository,
            User createdBy,
            TaskSourceType sourceType,
            String sourceReference,
            String title,
            String description,
            TaskStatus status,
            String branchName,
            String currentStep,
            String failureReason,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant finishedAt,
            String failureCode,
            String failureMessage,
            Instant failedAt) {
        this.organization = organization;
        this.repository = repository;
        this.createdBy = createdBy;
        this.sourceType = sourceType;
        this.sourceReference = sourceReference;
        this.title = title;
        this.description = description;
        this.status = status;
        this.branchName = branchName;
        this.currentStep = currentStep;
        this.failureReason = failureReason;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.failedAt = failedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Repository getRepository() {
        return repository;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public TaskSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public long getVersion() {
        return version;
    }

    public void transitionTo(TaskStatus nextStatus, Instant now) {
        this.status = nextStatus;
        this.updatedAt = now;
        if (nextStatus == TaskStatus.RUNNING && startedAt == null) {
            startedAt = now;
        }
        if (nextStatus == TaskStatus.SUCCEEDED
                || nextStatus == TaskStatus.FAILED
                || nextStatus == TaskStatus.CANCELLED) {
            finishedAt = now;
        }
    }

    public void recordFailure(String code, String message, Instant now) {
        this.failureCode = code;
        this.failureMessage = message;
        this.failedAt = now;
        this.failureReason = message;
    }
}
