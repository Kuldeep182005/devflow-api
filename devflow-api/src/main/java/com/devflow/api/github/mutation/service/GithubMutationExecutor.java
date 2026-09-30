package com.devflow.api.github.mutation.service;

import com.devflow.api.approval.domain.ApprovalStatus;
import com.devflow.api.github.integration.GitCommit;
import com.devflow.api.github.integration.GitRef;
import com.devflow.api.github.integration.GitTreeEntry;
import com.devflow.api.github.integration.GithubGitClient;
import com.devflow.api.github.integration.GithubIntegrationException;
import com.devflow.api.github.integration.GithubIntegrationService;
import com.devflow.api.github.integration.GithubPullRequestClient;
import com.devflow.api.github.integration.PullRequestResult;
import com.devflow.api.github.mutation.domain.GithubMutation;
import com.devflow.api.github.mutation.domain.GithubMutationStatus;
import com.devflow.api.persistence.githubmutation.GithubMutationRepository;
import com.devflow.api.persistence.task.TaskEventRepository;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeFile;
import com.devflow.api.proposedchange.domain.ProposedChangeOperation;
import com.devflow.api.proposedchange.domain.ProposedChangeStatus;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.sandbox.workspace.UnifiedDiffApplier;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskActor;
import com.devflow.api.task.domain.TaskActorSource;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.task.domain.TaskEventType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class GithubMutationExecutor {

    private static final Logger log = LoggerFactory.getLogger(GithubMutationExecutor.class);

    private final GithubMutationRepository githubMutationRepository;
    private final GithubIntegrationService githubIntegrationService;
    private final GithubGitClient gitClient;
    private final GithubPullRequestClient pullRequestClient;
    private final TaskEventRepository taskEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final com.devflow.api.observability.metrics.DevFlowMetrics metrics;

    public GithubMutationExecutor(
            GithubMutationRepository githubMutationRepository,
            GithubIntegrationService githubIntegrationService,
            GithubGitClient gitClient,
            GithubPullRequestClient pullRequestClient,
            TaskEventRepository taskEventRepository,
            TransactionTemplate transactionTemplate) {
        this(githubMutationRepository, githubIntegrationService, gitClient, pullRequestClient,
                taskEventRepository, transactionTemplate, (com.devflow.api.observability.metrics.DevFlowMetrics) null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GithubMutationExecutor(
            GithubMutationRepository githubMutationRepository,
            GithubIntegrationService githubIntegrationService,
            GithubGitClient gitClient,
            GithubPullRequestClient pullRequestClient,
            TaskEventRepository taskEventRepository,
            TransactionTemplate transactionTemplate,
            org.springframework.beans.factory.ObjectProvider<com.devflow.api.observability.metrics.DevFlowMetrics> metricsProvider) {
        this(githubMutationRepository, githubIntegrationService, gitClient, pullRequestClient,
                taskEventRepository, transactionTemplate, metricsProvider != null ? metricsProvider.getIfAvailable() : null);
    }

    public GithubMutationExecutor(
            GithubMutationRepository githubMutationRepository,
            GithubIntegrationService githubIntegrationService,
            GithubGitClient gitClient,
            GithubPullRequestClient pullRequestClient,
            TaskEventRepository taskEventRepository,
            TransactionTemplate transactionTemplate,
            com.devflow.api.observability.metrics.DevFlowMetrics metrics) {
        this.githubMutationRepository = githubMutationRepository;
        this.githubIntegrationService = githubIntegrationService;
        this.gitClient = gitClient;
        this.pullRequestClient = pullRequestClient;
        this.taskEventRepository = taskEventRepository;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }


    public void execute(UUID mutationId) {
        if (mutationId == null) {
            return;
        }

        // 1. Load authoritative mutation record and mark RUNNING
        Optional<GithubMutation> initialOpt = githubMutationRepository.findByIdWithDetails(mutationId);
        if (initialOpt.isEmpty()) {
            return;
        }

        GithubMutation mutation = initialOpt.get();
        if (mutation.getStatus() == GithubMutationStatus.SUCCEEDED) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            GithubMutation m = githubMutationRepository.findByIdWithDetails(mutationId).orElseThrow();
            if (m.getStatus() == GithubMutationStatus.QUEUED) {
                m.markRunning(Instant.now());
                githubMutationRepository.saveAndFlush(m);
            }
        });

        mutation = githubMutationRepository.findByIdWithDetails(mutationId).orElseThrow();
        ProposedChange proposedChange = mutation.getProposedChange();
        Repository repository = mutation.getRepository();
        Task task = mutation.getTask();

        try {
            // 2. Re-verify approval, proposal version, and sandbox execution status
            if (mutation.getApproval().getStatus() != ApprovalStatus.APPROVED) {
                recordFailure(mutationId, "APPROVAL_NOT_APPROVED", "Approval is not in APPROVED status");
                return;
            }
            if (proposedChange.getVersion() != mutation.getProposedChangeVersion()
                    || proposedChange.getStatus() != ProposedChangeStatus.VALIDATED) {
                recordFailure(mutationId, "PROPOSAL_VERSION_MISMATCH", "ProposedChange has changed since approval");
                return;
            }
            SandboxExecution sandboxExecution = mutation.getSandboxExecution();
            if (sandboxExecution.getStatus() != SandboxExecutionStatus.SUCCEEDED
                    || sandboxExecution.getProposedChangeVersion() != mutation.getProposedChangeVersion()) {
                recordFailure(mutationId, "SANDBOX_NOT_SUCCESSFUL", "Sandbox execution is not valid for this proposal version");
                return;
            }

            // 3. Obtain GitHub installation token
            if (repository.getGithubInstallationId() == null || repository.getGithubInstallationId() <= 0) {
                recordFailure(mutationId, "GITHUB_INSTALLATION_NOT_FOUND", "No GitHub App installation for repository");
                return;
            }
            String installationToken = githubIntegrationService.getInstallationToken(repository.getGithubInstallationId());

            String[] nameParts = repository.getFullName().split("/", 2);
            if (nameParts.length != 2) {
                recordFailure(mutationId, "INVALID_REPOSITORY", "Invalid repository full name");
                return;
            }
            String owner = nameParts[0];
            String repo = nameParts[1];
            String defaultBranch = repository.getDefaultBranch();
            String branchName = mutation.getBranchName();

            // 4. Reconciliation check: verify if PR was already created
            Optional<PullRequestResult> existingPr = pullRequestClient.findPullRequest(owner, repo, branchName, defaultBranch, installationToken);
            if (existingPr.isPresent()) {
                PullRequestResult pr = existingPr.get();
                recordPrSuccess(mutationId, pr.number(), pr.htmlUrl());
                return;
            }

            // 5. Stale base revision check: verify remote base branch SHA matches approved baseRevision
            Optional<GitRef> baseRefOpt = gitClient.getRef(owner, repo, "heads/" + defaultBranch, installationToken);
            if (baseRefOpt.isEmpty()) {
                recordFailure(mutationId, "GITHUB_NOT_FOUND", "Base branch " + defaultBranch + " not found on GitHub");
                return;
            }
            GitRef baseRef = baseRefOpt.get();
            if (!baseRef.sha().equalsIgnoreCase(mutation.getBaseRevision())) {
                recordFailure(mutationId, "BASE_REVISION_STALE",
                        "Remote base revision " + baseRef.sha() + " does not match approved base revision " + mutation.getBaseRevision());
                return;
            }

            // 6. Branch & Commit reconciliation / creation
            Optional<GitRef> existingBranchOpt = gitClient.getRef(owner, repo, "heads/" + branchName, installationToken);
            String commitSha;

            if (existingBranchOpt.isPresent()) {
                commitSha = existingBranchOpt.get().sha();
                recordBranchSuccess(mutationId, branchName);
                recordCommitSuccess(mutationId, commitSha);
            } else {
                // 6.1 Resolve base tree SHA
                Optional<GitCommit> baseCommitOpt = gitClient.getCommit(owner, repo, baseRef.sha(), installationToken);
                if (baseCommitOpt.isEmpty()) {
                    recordFailure(mutationId, "GITHUB_NOT_FOUND", "Base commit not found on remote");
                    return;
                }
                String baseTreeSha = baseCommitOpt.get().treeSha();

                // 6.2 Apply approved patch in memory and create git blobs
                List<GitTreeEntry> treeEntries = new ArrayList<>();
                for (ProposedChangeFile file : proposedChange.getFiles()) {
                    if (file.getOperation() == ProposedChangeOperation.ADD) {
                        String addedContent = UnifiedDiffApplier.extractAddedContent(file.getFilePatch());
                        Optional<String> remoteFile = gitClient.getFileContent(owner, repo, file.getPath(), baseRef.sha(), installationToken);
                        if (remoteFile.isPresent()) {
                            recordFailure(mutationId, "PATCH_APPLICATION_FAILED", "File to add already exists on remote: " + file.getPath());
                            return;
                        }
                        String blobSha = gitClient.createBlob(owner, repo, addedContent, installationToken);
                        treeEntries.add(new GitTreeEntry(file.getPath(), "100644", "blob", blobSha));
                    } else if (file.getOperation() == ProposedChangeOperation.MODIFY) {
                        Optional<String> remoteFile = gitClient.getFileContent(owner, repo, file.getPath(), baseRef.sha(), installationToken);
                        if (remoteFile.isEmpty()) {
                            recordFailure(mutationId, "PATCH_APPLICATION_FAILED", "File to modify does not exist on remote: " + file.getPath());
                            return;
                        }
                        String modifiedContent;
                        try {
                            modifiedContent = UnifiedDiffApplier.applyPatch(remoteFile.get(), file.getFilePatch());
                        } catch (Exception e) {
                            recordFailure(mutationId, "PATCH_APPLICATION_FAILED", "Failed to apply patch hunk: " + e.getMessage());
                            return;
                        }
                        String blobSha = gitClient.createBlob(owner, repo, modifiedContent, installationToken);
                        treeEntries.add(new GitTreeEntry(file.getPath(), "100644", "blob", blobSha));
                    } else if (file.getOperation() == ProposedChangeOperation.DELETE) {
                        Optional<String> remoteFile = gitClient.getFileContent(owner, repo, file.getPath(), baseRef.sha(), installationToken);
                        if (remoteFile.isEmpty()) {
                            recordFailure(mutationId, "PATCH_APPLICATION_FAILED", "File to delete does not exist on remote: " + file.getPath());
                            return;
                        }
                        treeEntries.add(new GitTreeEntry(file.getPath(), "100644", "blob", null));
                    }
                }

                // 6.3 Create Git Tree
                String newTreeSha = gitClient.createTree(owner, repo, baseTreeSha, treeEntries, installationToken);

                // 6.4 Create Commit
                String commitMessage = proposedChange.getTitle() + "\n\n"
                        + (proposedChange.getDescription() != null ? proposedChange.getDescription() : "")
                        + "\n\nTask: " + task.getId();
                commitSha = gitClient.createCommit(owner, repo, commitMessage, newTreeSha, List.of(baseRef.sha()), installationToken);
                recordCommitSuccess(mutationId, commitSha);

                // 6.5 Create Branch reference
                gitClient.createRef(owner, repo, "refs/heads/" + branchName, commitSha, installationToken);
                recordBranchSuccess(mutationId, branchName);
            }

            // 7. Create Pull Request
            String prTitle = proposedChange.getTitle();
            String prBody = "### DevFlow Automated Pull Request\n\n"
                    + (proposedChange.getDescription() != null ? proposedChange.getDescription() : "")
                    + "\n\n- Task: " + task.getTitle()
                    + "\n- Approved Proposal: " + proposedChange.getId() + " (v" + proposedChange.getVersion() + ")\n";

            PullRequestResult prResult = pullRequestClient.createPullRequest(owner, repo, prTitle, prBody, branchName, defaultBranch, installationToken);
            recordPrSuccess(mutationId, prResult.number(), prResult.htmlUrl());

        } catch (GithubIntegrationException e) {
            log.warn("GitHub integration error during mutation {}: {} - {}", mutationId, e.getCode(), e.getMessage());
            recordFailure(mutationId, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during GitHub mutation {}", mutationId, e);
            recordFailure(mutationId, "INTERNAL_ERROR", e.getMessage());
        }
    }

    private void recordCommitSuccess(UUID mutationId, String commitSha) {
        if (metrics != null) {
            metrics.recordGithubCommitCreated();
        }
        transactionTemplate.executeWithoutResult(status -> {
            GithubMutation mutation = githubMutationRepository.findByIdWithDetails(mutationId).orElseThrow();
            if (mutation.getCommitSha() == null) {
                mutation.recordCommit(commitSha, Instant.now());
                githubMutationRepository.saveAndFlush(mutation);
                String metadata = "{\"mutationId\":\"" + mutationId + "\",\"commitSha\":\"" + commitSha + "\"}";
                taskEventRepository.save(new TaskEvent(
                        mutation.getTask(),
                        mutation.getOrganization(),
                        TaskEventType.GITHUB_COMMIT_CREATED,
                        mutation.getTask().getStatus(),
                        mutation.getTask().getStatus(),
                        TaskActor.worker(),
                        Instant.now(),
                        null,
                        "mutation-commit-" + mutationId,
                        metadata
                ));
            }
        });
    }

    private void recordBranchSuccess(UUID mutationId, String branchName) {
        if (metrics != null) {
            metrics.recordGithubBranchCreated();
        }
        transactionTemplate.executeWithoutResult(status -> {
            GithubMutation mutation = githubMutationRepository.findByIdWithDetails(mutationId).orElseThrow();
            if (mutation.getStatus() != GithubMutationStatus.BRANCH_CREATED
                    && mutation.getStatus() != GithubMutationStatus.PR_CREATED
                    && mutation.getStatus() != GithubMutationStatus.SUCCEEDED) {
                mutation.recordBranch(branchName, Instant.now());
                githubMutationRepository.saveAndFlush(mutation);
                String metadata = "{\"mutationId\":\"" + mutationId + "\",\"branchName\":\"" + branchName + "\"}";
                taskEventRepository.save(new TaskEvent(
                        mutation.getTask(),
                        mutation.getOrganization(),
                        TaskEventType.GITHUB_BRANCH_CREATED,
                        mutation.getTask().getStatus(),
                        mutation.getTask().getStatus(),
                        TaskActor.worker(),
                        Instant.now(),
                        null,
                        "mutation-branch-" + mutationId,
                        metadata
                ));
            }
        });
    }

    private void recordPrSuccess(UUID mutationId, long prNumber, String prUrl) {
        if (metrics != null) {
            metrics.recordGithubPrCreated();
            metrics.recordGithubMutationSuccess(null);
        }
        transactionTemplate.executeWithoutResult(status -> {
            GithubMutation mutation = githubMutationRepository.findByIdWithDetails(mutationId).orElseThrow();
            if (mutation.getStatus() != GithubMutationStatus.SUCCEEDED) {
                mutation.recordPullRequest(prNumber, prUrl, Instant.now());
                mutation.markSucceeded(Instant.now());
                githubMutationRepository.saveAndFlush(mutation);
                String metadata = "{\"mutationId\":\"" + mutationId + "\",\"pullRequestNumber\":" + prNumber
                        + ",\"pullRequestUrl\":\"" + prUrl + "\"}";
                taskEventRepository.save(new TaskEvent(
                        mutation.getTask(),
                        mutation.getOrganization(),
                        TaskEventType.GITHUB_PR_CREATED,
                        mutation.getTask().getStatus(),
                        mutation.getTask().getStatus(),
                        TaskActor.worker(),
                        Instant.now(),
                        null,
                        "mutation-pr-" + mutationId,
                        metadata
                ));
            }
        });
    }

    private void recordFailure(UUID mutationId, String failureCode, String failureMessage) {
        if (metrics != null) {
            metrics.recordGithubMutationFailed(failureCode);
            if ("BASE_REVISION_STALE".equals(failureCode)) {
                metrics.recordGithubMutationStaleBase();
            }
        }
        transactionTemplate.executeWithoutResult(status -> {
            GithubMutation mutation = githubMutationRepository.findByIdWithDetails(mutationId).orElseThrow();
            mutation.fail(failureCode, failureMessage, Instant.now());

            githubMutationRepository.saveAndFlush(mutation);
            String safeMsg = failureMessage != null ? failureMessage.replace("\"", "'") : "";
            String metadata = "{\"mutationId\":\"" + mutationId + "\",\"failureCode\":\"" + failureCode
                    + "\",\"failureMessage\":\"" + safeMsg + "\"}";
            taskEventRepository.save(new TaskEvent(
                    mutation.getTask(),
                    mutation.getOrganization(),
                    TaskEventType.GITHUB_MUTATION_FAILED,
                    mutation.getTask().getStatus(),
                    mutation.getTask().getStatus(),
                    TaskActor.worker(),
                    Instant.now(),
                    null,
                    "mutation-fail-" + mutationId,
                    metadata
            ));
        });
    }
}
