package com.devflow.api.github.mutation.service;

import com.devflow.api.approval.domain.Approval;
import com.devflow.api.github.integration.GitCommit;
import com.devflow.api.github.integration.GitRef;
import com.devflow.api.github.integration.GitTreeEntry;
import com.devflow.api.github.integration.GithubGitClient;
import com.devflow.api.github.integration.GithubIntegrationService;
import com.devflow.api.github.integration.GithubPullRequestClient;
import com.devflow.api.github.integration.PullRequestResult;
import com.devflow.api.github.mutation.domain.GithubMutation;
import com.devflow.api.github.mutation.domain.GithubMutationStatus;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.persistence.githubmutation.GithubMutationRepository;
import com.devflow.api.persistence.task.TaskEventRepository;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeFile;
import com.devflow.api.proposedchange.domain.ProposedChangeOperation;
import com.devflow.api.proposedchange.domain.ProposedChangeStatus;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.task.domain.TaskEventType;
import com.devflow.api.task.domain.TaskSourceType;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubMutationExecutorTest {

    @Mock
    private GithubMutationRepository githubMutationRepository;

    @Mock
    private GithubIntegrationService githubIntegrationService;

    @Mock
    private GithubGitClient gitClient;

    @Mock
    private GithubPullRequestClient pullRequestClient;

    @Mock
    private TaskEventRepository taskEventRepository;

    private TransactionTemplate transactionTemplate;
    private GithubMutationExecutor executor;

    private UUID mutationId;
    private Organization org;
    private Repository repository;
    private Task task;
    private ProposedChange proposedChange;
    private SandboxExecution sandboxExecution;
    private Approval approval;
    private GithubMutation mutation;
    private User user;

    @BeforeEach
    void setUp() {
        transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action = invocation.getArgument(0);
            action.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        executor = new GithubMutationExecutor(
                githubMutationRepository,
                githubIntegrationService,
                gitClient,
                pullRequestClient,
                taskEventRepository,
                transactionTemplate
        );

        mutationId = UUID.randomUUID();
        Instant now = Instant.now();

        user = new User("user@example.com", "User", "auth0", true, now, now);
        org = new Organization("Org", "org", now, now);
        repository = new Repository(org, RepositoryProvider.GITHUB, "repo-1", "octocat/hello-world", "main", true, now, now);
        repository.associateGithubInstallation(12345L);

        task = new Task(org, repository, user, TaskSourceType.MANUAL, null, "Task", "Desc",
                TaskStatus.WAITING_FOR_APPROVAL, null, null, null, now, now, null, null);

        proposedChange = new ProposedChange(task, org, repository, ProposedChangeStatus.VALIDATED,
                "Add feature", "Detailed description", "base-sha-123456", "diff", "{}", now);

        sandboxExecution = new SandboxExecution(proposedChange, task, org, repository,
                SandboxExecutionProfile.JAVA_MAVEN_TEST, now);
        sandboxExecution.markRunning(now);
        sandboxExecution.complete(SandboxExecutionStatus.SUCCEEDED, 0, "SUCCESS", "", false, null, null, now);

        approval = new Approval(proposedChange, task, org, repository, proposedChange.getVersion(),
                sandboxExecution, user, now);
        approval.approve(user, "Approved", now);

        mutation = new GithubMutation(
                mutationId, org, repository, task, proposedChange, proposedChange.getVersion(),
                approval, sandboxExecution, "base-sha-123456", "devflow/task-1/change-1", now
        );
    }

    @Test
    @DisplayName("execute stops if remote base revision does not match approved base revision")
    void executeStopsIfBaseRevisionStale() {
        when(githubMutationRepository.findByIdWithDetails(mutationId)).thenReturn(Optional.of(mutation));
        when(githubIntegrationService.getInstallationToken(12345L)).thenReturn("ghs_token_xyz");

        // Remote base branch is at a newer/different SHA
        GitRef remoteBaseRef = new GitRef("refs/heads/main", "different-remote-sha-999");
        when(pullRequestClient.findPullRequest("octocat", "hello-world", mutation.getBranchName(), "main", "ghs_token_xyz"))
                .thenReturn(Optional.empty());
        when(gitClient.getRef("octocat", "hello-world", "heads/main", "ghs_token_xyz"))
                .thenReturn(Optional.of(remoteBaseRef));

        executor.execute(mutationId);

        assertThat(mutation.getStatus()).isEqualTo(GithubMutationStatus.FAILED);
        assertThat(mutation.getFailureCode()).isEqualTo("BASE_REVISION_STALE");
        assertThat(mutation.getFailureMessage()).contains("does not match approved base revision");

        verify(gitClient, never()).createCommit(anyString(), anyString(), anyString(), anyString(), anyList(), anyString());
        verify(gitClient, never()).createRef(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(pullRequestClient, never()).createPullRequest(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        ArgumentCaptor<TaskEvent> eventCaptor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(taskEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(TaskEventType.GITHUB_MUTATION_FAILED);
        assertThat(eventCaptor.getValue().getMetadata()).contains("BASE_REVISION_STALE");
    }

    @Test
    @DisplayName("execute reconciles existing PR on GitHub and marks SUCCEEDED without creating duplicates")
    void executeReconcilesExistingPr() {
        when(githubMutationRepository.findByIdWithDetails(mutationId)).thenReturn(Optional.of(mutation));
        when(githubIntegrationService.getInstallationToken(12345L)).thenReturn("ghs_token_xyz");

        PullRequestResult existingPr = new PullRequestResult(42L, "https://github.com/octocat/hello-world/pull/42", "open");
        when(pullRequestClient.findPullRequest("octocat", "hello-world", mutation.getBranchName(), "main", "ghs_token_xyz"))
                .thenReturn(Optional.of(existingPr));

        executor.execute(mutationId);

        assertThat(mutation.getStatus()).isEqualTo(GithubMutationStatus.SUCCEEDED);
        assertThat(mutation.getPullRequestNumber()).isEqualTo(42L);
        assertThat(mutation.getPullRequestUrl()).isEqualTo("https://github.com/octocat/hello-world/pull/42");

        verify(gitClient, never()).createTree(anyString(), anyString(), anyString(), anyList(), anyString());
        verify(gitClient, never()).createCommit(anyString(), anyString(), anyString(), anyString(), anyList(), anyString());
        verify(pullRequestClient, never()).createPullRequest(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        ArgumentCaptor<TaskEvent> eventCaptor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(taskEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(TaskEventType.GITHUB_PR_CREATED);
        assertThat(eventCaptor.getValue().getMetadata()).contains("https://github.com/octocat/hello-world/pull/42");
    }

    @Test
    @DisplayName("execute full successful pipeline: creates blobs, tree, commit, branch, PR, and emits audit events")
    void executeFullSuccessfulPipeline() {
        // Add files to proposedChange
        String addDiff = "diff --git a/NewFile.java b/NewFile.java\n--- /dev/null\n+++ b/NewFile.java\n@@ -0,0 +1,1 @@\n+class NewFile {}";
        ProposedChangeFile addFile = new ProposedChangeFile("NewFile.java", ProposedChangeOperation.ADD, 1, 0, addDiff);
        proposedChange.addFile(addFile);

        when(githubMutationRepository.findByIdWithDetails(mutationId)).thenReturn(Optional.of(mutation));
        when(githubIntegrationService.getInstallationToken(12345L)).thenReturn("ghs_token_xyz");

        when(pullRequestClient.findPullRequest("octocat", "hello-world", mutation.getBranchName(), "main", "ghs_token_xyz"))
                .thenReturn(Optional.empty());

        GitRef baseRef = new GitRef("refs/heads/main", "base-sha-123456");
        when(gitClient.getRef("octocat", "hello-world", "heads/main", "ghs_token_xyz"))
                .thenReturn(Optional.of(baseRef));

        // Branch does not exist yet
        when(gitClient.getRef("octocat", "hello-world", "heads/" + mutation.getBranchName(), "ghs_token_xyz"))
                .thenReturn(Optional.empty());

        GitCommit baseCommit = new GitCommit("base-sha-123456", "base-tree-sha-789");
        when(gitClient.getCommit("octocat", "hello-world", "base-sha-123456", "ghs_token_xyz"))
                .thenReturn(Optional.of(baseCommit));

        // File to add should NOT exist on remote
        when(gitClient.getFileContent("octocat", "hello-world", "NewFile.java", "base-sha-123456", "ghs_token_xyz"))
                .thenReturn(Optional.empty());

        when(gitClient.createBlob("octocat", "hello-world", "class NewFile {}", "ghs_token_xyz"))
                .thenReturn("blob-sha-newfile");

        when(gitClient.createTree(eq("octocat"), eq("hello-world"), eq("base-tree-sha-789"), anyList(), eq("ghs_token_xyz")))
                .thenReturn("new-tree-sha-999");

        when(gitClient.createCommit(eq("octocat"), eq("hello-world"), anyString(), eq("new-tree-sha-999"), eq(List.of("base-sha-123456")), eq("ghs_token_xyz")))
                .thenReturn("commit-sha-new");

        when(gitClient.createRef(eq("octocat"), eq("hello-world"), eq("refs/heads/" + mutation.getBranchName()), eq("commit-sha-new"), eq("ghs_token_xyz")))
                .thenReturn(new GitRef("refs/heads/" + mutation.getBranchName(), "commit-sha-new"));

        PullRequestResult createdPr = new PullRequestResult(101L, "https://github.com/octocat/hello-world/pull/101", "open");
        when(pullRequestClient.createPullRequest(eq("octocat"), eq("hello-world"), eq("Add feature"), anyString(), eq(mutation.getBranchName()), eq("main"), eq("ghs_token_xyz")))
                .thenReturn(createdPr);

        executor.execute(mutationId);

        assertThat(mutation.getStatus()).isEqualTo(GithubMutationStatus.SUCCEEDED);
        assertThat(mutation.getCommitSha()).isEqualTo("commit-sha-new");
        assertThat(mutation.getPullRequestNumber()).isEqualTo(101L);
        assertThat(mutation.getPullRequestUrl()).isEqualTo("https://github.com/octocat/hello-world/pull/101");

        ArgumentCaptor<TaskEvent> eventCaptor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(taskEventRepository, atLeastOnce()).save(eventCaptor.capture());
        List<TaskEventType> eventTypes = eventCaptor.getAllValues().stream().map(TaskEvent::getEventType).toList();
        assertThat(eventTypes).contains(
                TaskEventType.GITHUB_COMMIT_CREATED,
                TaskEventType.GITHUB_BRANCH_CREATED,
                TaskEventType.GITHUB_PR_CREATED
        );
    }

    @Test
    @DisplayName("execute fails if an ADD file already exists on remote")
    void executeFailsIfAddFileExistsOnRemote() {
        String addDiff = "diff --git a/Existing.java b/Existing.java\n--- /dev/null\n+++ b/Existing.java\n@@ -0,0 +1,1 @@\n+class Existing {}";
        proposedChange.addFile(new ProposedChangeFile("Existing.java", ProposedChangeOperation.ADD, 1, 0, addDiff));

        when(githubMutationRepository.findByIdWithDetails(mutationId)).thenReturn(Optional.of(mutation));
        when(githubIntegrationService.getInstallationToken(12345L)).thenReturn("ghs_token_xyz");
        when(pullRequestClient.findPullRequest("octocat", "hello-world", mutation.getBranchName(), "main", "ghs_token_xyz"))
                .thenReturn(Optional.empty());

        GitRef baseRef = new GitRef("refs/heads/main", "base-sha-123456");
        when(gitClient.getRef("octocat", "hello-world", "heads/main", "ghs_token_xyz"))
                .thenReturn(Optional.of(baseRef));
        when(gitClient.getRef("octocat", "hello-world", "heads/" + mutation.getBranchName(), "ghs_token_xyz"))
                .thenReturn(Optional.empty());

        GitCommit baseCommit = new GitCommit("base-sha-123456", "base-tree-sha-789");
        when(gitClient.getCommit("octocat", "hello-world", "base-sha-123456", "ghs_token_xyz"))
                .thenReturn(Optional.of(baseCommit));

        // Existing file on remote
        when(gitClient.getFileContent("octocat", "hello-world", "Existing.java", "base-sha-123456", "ghs_token_xyz"))
                .thenReturn(Optional.of("class Old {}"));

        executor.execute(mutationId);

        assertThat(mutation.getStatus()).isEqualTo(GithubMutationStatus.FAILED);
        assertThat(mutation.getFailureCode()).isEqualTo("PATCH_APPLICATION_FAILED");
        assertThat(mutation.getFailureMessage()).contains("already exists on remote");

        verify(gitClient, never()).createCommit(anyString(), anyString(), anyString(), anyString(), anyList(), anyString());
    }
}
