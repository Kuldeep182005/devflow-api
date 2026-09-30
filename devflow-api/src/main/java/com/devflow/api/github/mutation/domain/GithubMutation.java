package com.devflow.api.github.mutation.domain;

import com.devflow.api.approval.domain.Approval;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.task.domain.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "github_mutations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "github_mutations_unique_proposal_version",
                        columnNames = {"organization_id", "repository_id", "proposed_change_id", "proposed_change_version"}
                )
        }
)
public class GithubMutation {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposed_change_id", nullable = false)
    private ProposedChange proposedChange;

    @Column(name = "proposed_change_version", nullable = false)
    private long proposedChangeVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_id", nullable = false)
    private Approval approval;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sandbox_execution_id", nullable = false)
    private SandboxExecution sandboxExecution;

    @Column(name = "base_revision", nullable = false, length = 255)
    private String baseRevision;

    @Column(name = "branch_name", nullable = false, length = 255)
    private String branchName;

    @Column(name = "commit_sha", length = 255)
    private String commitSha;

    @Column(name = "pull_request_number")
    private Long pullRequestNumber;

    @Column(name = "pull_request_url", length = 1000)
    private String pullRequestUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GithubMutationStatus status;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected GithubMutation() {
    }

    public GithubMutation(
            Organization organization,
            Repository repository,
            Task task,
            ProposedChange proposedChange,
            long proposedChangeVersion,
            Approval approval,
            SandboxExecution sandboxExecution,
            String baseRevision,
            String branchName,
            Instant now) {
        this(UUID.randomUUID(), organization, repository, task, proposedChange, proposedChangeVersion,
                approval, sandboxExecution, baseRevision, branchName, now);
    }

    public GithubMutation(
            UUID id,
            Organization organization,
            Repository repository,
            Task task,
            ProposedChange proposedChange,
            long proposedChangeVersion,
            Approval approval,
            SandboxExecution sandboxExecution,
            String baseRevision,
            String branchName,
            Instant now) {
        if (id == null || organization == null || repository == null || task == null
                || proposedChange == null || approval == null || sandboxExecution == null
                || baseRevision == null || branchName == null || now == null) {
            throw new IllegalArgumentException("Required parameters for GithubMutation cannot be null");
        }
        this.id = id;
        this.organization = organization;
        this.repository = repository;
        this.task = task;
        this.proposedChange = proposedChange;
        this.proposedChangeVersion = proposedChangeVersion;
        this.approval = approval;
        this.sandboxExecution = sandboxExecution;
        this.baseRevision = baseRevision;
        this.branchName = branchName;
        this.status = GithubMutationStatus.QUEUED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean canTransitionTo(GithubMutationStatus target) {
        return this.status.canTransitionTo(target);
    }

    public void markRunning(Instant now) {
        if (!canTransitionTo(GithubMutationStatus.RUNNING)) {
            throw new IllegalStateException("Cannot transition from " + status + " to RUNNING");
        }
        this.status = GithubMutationStatus.RUNNING;
        this.updatedAt = now != null ? now : Instant.now();
    }

    public void recordCommit(String commitSha, Instant now) {
        if (commitSha == null || commitSha.isBlank()) {
            throw new IllegalArgumentException("Commit SHA cannot be blank");
        }
        if (!canTransitionTo(GithubMutationStatus.COMMITTED)) {
            throw new IllegalStateException("Cannot transition from " + status + " to COMMITTED");
        }
        this.commitSha = commitSha;
        this.status = GithubMutationStatus.COMMITTED;
        this.updatedAt = now != null ? now : Instant.now();
    }

    public void recordBranch(String branchName, Instant now) {
        if (branchName == null || branchName.isBlank()) {
            throw new IllegalArgumentException("Branch name cannot be blank");
        }
        if (!canTransitionTo(GithubMutationStatus.BRANCH_CREATED)) {
            throw new IllegalStateException("Cannot transition from " + status + " to BRANCH_CREATED");
        }
        this.branchName = branchName;
        this.status = GithubMutationStatus.BRANCH_CREATED;
        this.updatedAt = now != null ? now : Instant.now();
    }

    public void recordPullRequest(long prNumber, String prUrl, Instant now) {
        if (prNumber <= 0 || prUrl == null || prUrl.isBlank()) {
            throw new IllegalArgumentException("Invalid PR number or URL");
        }
        if (!canTransitionTo(GithubMutationStatus.PR_CREATED)) {
            throw new IllegalStateException("Cannot transition from " + status + " to PR_CREATED");
        }
        this.pullRequestNumber = prNumber;
        this.pullRequestUrl = prUrl;
        this.status = GithubMutationStatus.PR_CREATED;
        this.updatedAt = now != null ? now : Instant.now();
    }

    public void markSucceeded(Instant now) {
        if (!canTransitionTo(GithubMutationStatus.SUCCEEDED)) {
            throw new IllegalStateException("Cannot transition from " + status + " to SUCCEEDED");
        }
        this.status = GithubMutationStatus.SUCCEEDED;
        this.updatedAt = now != null ? now : Instant.now();
    }

    public void fail(String failureCode, String failureMessage, Instant now) {
        this.status = GithubMutationStatus.FAILED;
        this.failureCode = failureCode != null && failureCode.length() > 100
                ? failureCode.substring(0, 100) : failureCode;
        this.failureMessage = failureMessage != null && failureMessage.length() > 1000
                ? failureMessage.substring(0, 1000) : failureMessage;
        this.updatedAt = now != null ? now : Instant.now();
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

    public Task getTask() {
        return task;
    }

    public ProposedChange getProposedChange() {
        return proposedChange;
    }

    public long getProposedChangeVersion() {
        return proposedChangeVersion;
    }

    public Approval getApproval() {
        return approval;
    }

    public SandboxExecution getSandboxExecution() {
        return sandboxExecution;
    }

    public String getBaseRevision() {
        return baseRevision;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public Long getPullRequestNumber() {
        return pullRequestNumber;
    }

    public String getPullRequestUrl() {
        return pullRequestUrl;
    }

    public GithubMutationStatus getStatus() {
        return status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
