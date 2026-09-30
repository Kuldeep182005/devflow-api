package com.devflow.api.sandbox.domain;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.task.domain.Task;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sandbox_executions")
public class SandboxExecution {

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
    @Column(nullable = false, length = 30)
    private SandboxExecutionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SandboxExecutionProfile profile;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration")
    private Long duration;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(length = 20000)
    private String stdout;

    @Column(length = 20000)
    private String stderr;

    @Column(name = "output_truncated", nullable = false)
    private boolean outputTruncated;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 4000)
    private String failureMessage;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "proposed_change_version", nullable = false)
    private long proposedChangeVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SandboxExecution() {
    }

    public SandboxExecution(
            ProposedChange proposedChange,
            Task task,
            Organization organization,
            Repository repository,
            SandboxExecutionProfile profile,
            Instant now) {
        this.proposedChange = Objects.requireNonNull(proposedChange, "proposedChange must not be null");
        this.task = Objects.requireNonNull(task, "task must not be null");
        this.organization = Objects.requireNonNull(organization, "organization must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.status = SandboxExecutionStatus.QUEUED;
        this.proposedChangeVersion = proposedChange.getVersion();
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
        this.outputTruncated = false;
    }

    public void assignIdForTesting(UUID id) {
        this.id = id;
    }


    public boolean markRunning(Instant now) {
        if (!this.status.canTransitionTo(SandboxExecutionStatus.RUNNING)) {
            return false;
        }
        this.status = SandboxExecutionStatus.RUNNING;
        this.startedAt = now;
        this.updatedAt = now;
        return true;
    }

    public boolean complete(
            SandboxExecutionStatus finalStatus,
            Integer exitCode,
            String stdout,
            String stderr,
            boolean outputTruncated,
            String failureCode,
            String failureMessage,
            Instant now) {
        Objects.requireNonNull(finalStatus, "finalStatus must not be null");
        if (!finalStatus.isTerminal()) {
            throw new IllegalArgumentException("Target completion status must be terminal: " + finalStatus);
        }
        if (!this.status.canTransitionTo(finalStatus)) {
            return false;
        }
        this.status = finalStatus;
        this.exitCode = exitCode;
        this.stdout = sanitizeOutput(stdout);
        this.stderr = sanitizeOutput(stderr);
        this.outputTruncated = outputTruncated || isTruncatedLength(stdout) || isTruncatedLength(stderr);
        this.failureCode = safeSubstring(failureCode, 100);
        this.failureMessage = safeSubstring(failureMessage, 4000);
        this.finishedAt = now;
        this.updatedAt = now;
        if (this.startedAt != null) {
            this.duration = Math.max(0, Duration.between(this.startedAt, now).toMillis());
        }
        return true;
    }

    public boolean markCancelled(Instant now) {
        if (!this.status.canTransitionTo(SandboxExecutionStatus.CANCELLED)) {
            return false;
        }
        this.status = SandboxExecutionStatus.CANCELLED;
        this.finishedAt = now;
        this.updatedAt = now;
        if (this.startedAt != null) {
            this.duration = Math.max(0, Duration.between(this.startedAt, now).toMillis());
        }
        return true;
    }

    private static String sanitizeOutput(String output) {
        if (output == null) {
            return null;
        }
        if (output.length() > 20000) {
            return output.substring(0, 20000);
        }
        return output;
    }

    private static boolean isTruncatedLength(String output) {
        return output != null && output.length() > 20000;
    }

    private static String safeSubstring(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    public UUID getId() {
        return id;
    }

    public ProposedChange getProposedChange() {
        return proposedChange;
    }

    public Task getTask() {
        return task;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Repository getRepository() {
        return repository;
    }

    public SandboxExecutionStatus getStatus() {
        return status;
    }

    public SandboxExecutionProfile getProfile() {
        return profile;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Long getDuration() {
        return duration;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public boolean isOutputTruncated() {
        return outputTruncated;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public long getVersion() {
        return version;
    }

    public long getProposedChangeVersion() {
        return proposedChangeVersion;
    }

    public void setProposedChangeVersionForTesting(long proposedChangeVersion) {
        this.proposedChangeVersion = proposedChangeVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
