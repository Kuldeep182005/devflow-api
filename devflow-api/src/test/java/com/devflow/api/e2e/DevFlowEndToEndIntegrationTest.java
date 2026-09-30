package com.devflow.api.e2e;

import com.devflow.api.agent.domain.AgentExecutionResult;
import com.devflow.api.agent.domain.AgentState;
import com.devflow.api.agent.service.AgentExecutionService;
import com.devflow.api.approval.api.ApproveApprovalRequest;
import com.devflow.api.approval.api.RejectApprovalRequest;
import com.devflow.api.approval.api.RequestApprovalRequest;
import com.devflow.api.approval.domain.Approval;
import com.devflow.api.approval.domain.ApprovalStatus;
import com.devflow.api.approval.service.ApprovalService;
import com.devflow.api.github.integration.GitCommit;
import com.devflow.api.github.integration.GitRef;
import com.devflow.api.github.integration.GithubGitClient;
import com.devflow.api.github.integration.GithubIntegrationService;
import com.devflow.api.github.integration.GithubPullRequestClient;
import com.devflow.api.github.integration.PullRequestResult;
import com.devflow.api.github.mutation.domain.GithubMutation;
import com.devflow.api.github.mutation.domain.GithubMutationStatus;
import com.devflow.api.github.mutation.service.GithubMutationExecutor;
import com.devflow.api.github.mutation.service.GithubMutationService;
import com.devflow.api.llm.api.LlmRequest;
import com.devflow.api.llm.api.LlmResponse;
import com.devflow.api.llm.exception.LlmException;
import com.devflow.api.llm.provider.LlmProvider;
import com.devflow.api.observability.evaluation.AiEvaluation;
import com.devflow.api.observability.evaluation.AiEvaluationRepository;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.persistence.approval.ApprovalRepository;
import com.devflow.api.persistence.githubmutation.GithubMutationRepository;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.organization.OrganizationRepository;
import com.devflow.api.persistence.proposedchange.ProposedChangeRepository;
import com.devflow.api.persistence.repository.RepositoryRepository;
import com.devflow.api.persistence.sandbox.SandboxExecutionRepository;
import com.devflow.api.persistence.task.TaskEventRepository;
import com.devflow.api.persistence.task.TaskRepository;
import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.proposedchange.api.ProposedChangeCommand;
import com.devflow.api.proposedchange.api.ProposedChangeFileCommand;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeOperation;
import com.devflow.api.proposedchange.domain.ProposedChangeStatus;
import com.devflow.api.proposedchange.service.ProposedChangeService;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.sandbox.executor.SandboxExecutionRequest;
import com.devflow.api.sandbox.executor.SandboxExecutionResult;
import com.devflow.api.sandbox.executor.SandboxExecutor;
import com.devflow.api.sandbox.service.SandboxExecutionService;
import com.devflow.api.task.api.CreateTaskRequest;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskActor;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.task.domain.TaskEventType;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.task.service.TaskService;
import com.devflow.api.task.service.TaskStateService;
import com.devflow.api.user.domain.User;
import com.devflow.api.web.ApiException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "async.worker.enabled=false",
        "async.outbox.poll-interval-ms=60000",
        "agent.enabled=true",
        "sandbox.enabled=true",
        "llm.enabled=true",
        "repository-tools.enabled=true"
})
@AutoConfigureMockMvc
@Testcontainers
class DevFlowEndToEndIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository memberRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskEventRepository taskEventRepository;

    @Autowired
    private ProposedChangeRepository proposedChangeRepository;

    @Autowired
    private SandboxExecutionRepository sandboxExecutionRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private GithubMutationRepository githubMutationRepository;

    @Autowired
    private AiEvaluationRepository aiEvaluationRepository;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskStateService taskStateService;

    @Autowired
    private AgentExecutionService agentExecutionService;

    @Autowired
    private ProposedChangeService proposedChangeService;

    @Autowired
    private SandboxExecutionService sandboxExecutionService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private GithubMutationService githubMutationService;

    @Autowired
    private GithubMutationExecutor githubMutationExecutor;

    @MockBean
    private LlmProvider llmProvider;

    @MockBean
    private SandboxExecutor sandboxExecutor;

    @MockBean
    private GithubIntegrationService githubIntegrationService;

    @MockBean
    private GithubGitClient gitClient;

    @MockBean
    private GithubPullRequestClient pullRequestClient;

    private User owner;
    private Organization org;
    private Repository repo;

    @BeforeEach
    void setUp() {
        githubMutationRepository.deleteAll();
        approvalRepository.deleteAll();
        sandboxExecutionRepository.deleteAll();
        proposedChangeRepository.deleteAll();
        aiEvaluationRepository.deleteAll();
        taskEventRepository.deleteAll();
        taskRepository.deleteAll();
        repositoryRepository.deleteAll();
        memberRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();

        Instant now = Instant.now();
        owner = userRepository.saveAndFlush(new User("owner-" + now.toEpochMilli() + "@devflow.io",
                "Owner User", "auth0|" + now.toEpochMilli(), true, now, now));

        org = organizationRepository.saveAndFlush(new Organization("DevFlow Team", "devflow-" + now.toEpochMilli(), now, now));

        memberRepository.saveAndFlush(new OrganizationMember(org, owner, OrganizationRole.OWNER, now));

        repo = repositoryRepository.saveAndFlush(new Repository(org, RepositoryProvider.GITHUB, "devflow/demo-service",
                "devflow/demo-service", "main", true, now, now));
        repo.associateGithubInstallation(12345L);
        repositoryRepository.saveAndFlush(repo);

        when(githubIntegrationService.getInstallationToken(anyLong())).thenReturn("ghs_test_token_secret");
    }

    @Test
    @DisplayName("Scenario A: Complete Happy Path E2E Workflow")
    void scenarioA_happyPathCompleteWorkflow() {
        // 1. Authenticated user creates task
        CreateTaskRequest createReq = new CreateTaskRequest(repo.getId(), "Fix health check null pointer", "Investigate /health endpoint null pointer on boot");
        Task task = taskService.create(owner.getId(), org.getId(), createReq);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(task.getOrganization().getId()).isEqualTo(org.getId());
        assertThat(task.getRepository().getId()).isEqualTo(repo.getId());

        // Verify TASK_CREATED event
        List<TaskEvent> events = taskEventRepository.findAllByTaskIdAndOrganizationId(task.getId(), org.getId(), Pageable.unpaged()).getContent();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(TaskEventType.TASK_CREATED);

        // 2. Worker claims task and starts execution: QUEUED -> RUNNING
        task = taskStateService.startTask(org.getId(), task.getId(), TaskActor.worker(), "claim-" + task.getId());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);

        // 3. Agent execution executes bounded LLM loop
        when(llmProvider.providerName()).thenReturn("mock-openai");
        when(llmProvider.complete(any(LlmRequest.class))).thenReturn(new LlmResponse(
                """
                {
                  "action": "FINISH",
                  "reasoningSummary": "Identified null check fix in HealthController",
                  "finalResponse": "Fixed null pointer by adding null check before reading system status."
                }
                """,
                "mock-openai", "gpt-4", "stop", 50, 100, Instant.now()
        ));

        AgentExecutionResult agentResult = agentExecutionService.execute(owner.getId(), org.getId(), task.getId());
        assertThat(agentResult.state()).isEqualTo(AgentState.SUCCEEDED);
        assertThat(agentResult.response()).contains("Fixed null pointer");

        // 4. Proposed change created and validated
        String unifiedDiff = """
                diff --git a/src/HealthController.java b/src/HealthController.java
                new file mode 100644
                --- /dev/null
                +++ b/src/HealthController.java
                @@ -0,0 +1,3 @@
                +public class HealthController {
                +    // health
                +}
                """;

        com.devflow.api.agent.domain.AgentExecutionContext agentCtx = new com.devflow.api.agent.domain.AgentExecutionContext(
                owner.getId(), org.getId(), task.getId(), repo.getId(), "Fix bug", repo.getFullName());

        ProposedChangeFileCommand fileCmd = new ProposedChangeFileCommand(
                "src/HealthController.java",
                ProposedChangeOperation.ADD,
                3, 0,
                unifiedDiff
        );

        ProposedChangeCommand proposalCmd = new ProposedChangeCommand(
                "Fix health check null pointer",
                "Adds null safety to HealthController",
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                unifiedDiff,
                List.of(fileCmd)
        );

        ProposedChange proposal = proposedChangeService.create(agentCtx, proposalCmd);
        assertThat(proposal.getStatus()).isEqualTo(ProposedChangeStatus.VALIDATED);
        assertThat(proposal.getVersion()).isEqualTo(0);

        // 5. Sandbox execution queued and executed
        when(sandboxExecutor.execute(any(SandboxExecutionRequest.class), any(Path.class)))
                .thenReturn(SandboxExecutionResult.succeeded(
                        UUID.randomUUID(), proposal.getId(), 0, Duration.ofMillis(250), "All 12 tests passed", "", false));

        SandboxExecution sandboxExecution = sandboxExecutionService.queueExecution(
                proposal.getId(), org.getId(), SandboxExecutionProfile.JAVA_MAVEN_TEST);
        assertThat(sandboxExecution.getStatus()).isEqualTo(SandboxExecutionStatus.QUEUED);

        sandboxExecution = sandboxExecutionService.executeExecution(sandboxExecution.getId());
        assertThat(sandboxExecution.getStatus()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(sandboxExecution.getExitCode()).isEqualTo(0);

        // 6. Task transitions to WAITING_FOR_APPROVAL
        task = taskStateService.requestApproval(org.getId(), task.getId(), TaskActor.worker(), "req-approval-" + task.getId());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.WAITING_FOR_APPROVAL);

        // 7. Request approval explicitly
        Approval approval = approvalService.requestApproval(owner.getId(), org.getId(),
                new RequestApprovalRequest(proposal.getId()));
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approval.getProposedChangeVersion()).isEqualTo(proposal.getVersion());

        // 8. Human Owner approves the change
        approval = approvalService.approve(owner.getId(), org.getId(), approval.getId(),
                new ApproveApprovalRequest("LGTM, verified sandbox results."));
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getDecidedBy().getId()).isEqualTo(owner.getId());

        // 9. Enqueue GitHub Mutation
        var mutationResponse = githubMutationService.enqueueExecution(owner.getId(), org.getId(), approval.getId());
        assertThat(mutationResponse.status()).isEqualTo("QUEUED");
        UUID mutationId = mutationResponse.id();

        // 10. Execute GitHub mutation with deterministic test double
        String baseSha = proposal.getBaseRevision();
        when(pullRequestClient.findPullRequest(eq("devflow"), eq("demo-service"), anyString(), eq("main"), anyString()))
                .thenReturn(Optional.empty());
        when(gitClient.getRef(eq("devflow"), eq("demo-service"), eq("heads/main"), anyString()))
                .thenReturn(Optional.of(new GitRef("refs/heads/main", baseSha)));
        when(gitClient.getRef(eq("devflow"), eq("demo-service"), startsWith("heads/devflow/"), anyString()))
                .thenReturn(Optional.empty());
        when(gitClient.getCommit(eq("devflow"), eq("demo-service"), eq(baseSha), anyString()))
                .thenReturn(Optional.of(new GitCommit(baseSha, "tree-base-sha-123")));
        when(gitClient.getFileContent(eq("devflow"), eq("demo-service"), eq("src/HealthController.java"), eq(baseSha), anyString()))
                .thenReturn(Optional.empty());
        when(gitClient.createBlob(eq("devflow"), eq("demo-service"), anyString(), anyString()))
                .thenReturn("blob-sha-456");
        when(gitClient.createTree(eq("devflow"), eq("demo-service"), eq("tree-base-sha-123"), any(), anyString()))
                .thenReturn("tree-new-sha-789");
        when(gitClient.createCommit(eq("devflow"), eq("demo-service"), anyString(), eq("tree-new-sha-789"), eq(List.of(baseSha)), anyString()))
                .thenReturn("commit-new-sha-abc");
        when(gitClient.createRef(eq("devflow"), eq("demo-service"), anyString(), eq("commit-new-sha-abc"), anyString()))
                .thenReturn(new GitRef("refs/heads/devflow/task-" + task.getId(), "commit-new-sha-abc"));
        when(pullRequestClient.createPullRequest(eq("devflow"), eq("demo-service"), anyString(), anyString(), anyString(), eq("main"), anyString()))
                .thenReturn(new PullRequestResult(42, "https://github.com/devflow/demo-service/pull/42", "open"));

        githubMutationExecutor.execute(mutationId);

        GithubMutation completedMutation = githubMutationRepository.findByIdWithDetails(mutationId).orElseThrow();
        assertThat(completedMutation.getStatus()).isEqualTo(GithubMutationStatus.SUCCEEDED);
        assertThat(completedMutation.getPullRequestNumber()).isEqualTo(42);
        assertThat(completedMutation.getPullRequestUrl()).isEqualTo("https://github.com/devflow/demo-service/pull/42");

        // 11. Final task transition: resume to RUNNING after approval and mutation, then transition to SUCCEEDED
        task = taskStateService.startTask(org.getId(), task.getId(), TaskActor.worker(), "resume-" + task.getId());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);

        task = taskStateService.succeedTask(org.getId(), task.getId(), TaskActor.worker(), "succeed-" + task.getId());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);

        // 12. Verify complete event audit trail
        List<TaskEvent> allEvents = taskEventRepository.findAllByTaskIdAndOrganizationId(task.getId(), org.getId(), Pageable.unpaged()).getContent();
        List<TaskEventType> eventTypes = allEvents.stream().map(TaskEvent::getEventType).toList();
        assertThat(eventTypes).contains(
                TaskEventType.TASK_CREATED,
                TaskEventType.TASK_STARTED,
                TaskEventType.TASK_APPROVAL_REQUIRED,
                TaskEventType.APPROVAL_REQUESTED,
                TaskEventType.APPROVAL_APPROVED,
                TaskEventType.GITHUB_BRANCH_CREATED,
                TaskEventType.GITHUB_COMMIT_CREATED,
                TaskEventType.GITHUB_PR_CREATED,
                TaskEventType.TASK_SUCCEEDED
        );

        // 13. Verify AI evaluation linkage
        List<AiEvaluation> evaluations = aiEvaluationRepository.findByTaskIdOrderByStartedAtDesc(task.getId());
        assertThat(evaluations).isNotEmpty();
        AiEvaluation eval = evaluations.get(0);
        assertThat(eval.getFinalState()).isEqualTo("SUCCEEDED");
        assertThat(eval.getIterationCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Scenario B: Agent Failure produces controlled FAILED state without leaking exceptions")
    void scenarioB_agentFailureHandling() {
        CreateTaskRequest createReq = new CreateTaskRequest(repo.getId(), "Task with failing LLM", "Prompt triggering outage");
        Task task = taskService.create(owner.getId(), org.getId(), createReq);
        task = taskStateService.startTask(org.getId(), task.getId(), TaskActor.worker(), "claim-b-" + task.getId());

        when(llmProvider.providerName()).thenReturn("mock-openai");
        when(llmProvider.complete(any(LlmRequest.class))).thenThrow(new LlmException("Simulated upstream 503 Service Unavailable"));

        AgentExecutionResult agentResult = agentExecutionService.execute(owner.getId(), org.getId(), task.getId());
        assertThat(agentResult.state()).isEqualTo(AgentState.FAILED);

        // Fail task with controlled failure code
        task = taskStateService.failTask(org.getId(), task.getId(), TaskActor.worker(),
                "fail-b-" + task.getId(), "EXTERNAL_SERVICE_FAILURE", "LLM provider outage");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getFailureCode()).isEqualTo("EXTERNAL_SERVICE_FAILURE");

        UUID failedTaskId = task.getId();
        UUID orgId = org.getId();
        // Verify terminal state cannot be changed
        assertThatThrownBy(() -> taskStateService.startTask(orgId, failedTaskId, TaskActor.worker(), "retry-" + failedTaskId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("Scenario C: Sandbox Failure prevents approval and does not auto-approve")
    void scenarioC_sandboxFailurePreventsApproval() {
        Task task = taskService.create(owner.getId(), org.getId(), new CreateTaskRequest(repo.getId(), "Sandbox fail task", "Test failure"));
        task = taskStateService.startTask(org.getId(), task.getId(), TaskActor.worker(), "start-c-" + task.getId());

        ProposedChange proposal = createTestProposal(task);

        // Sandbox fails with compilation error
        when(sandboxExecutor.execute(any(), any())).thenReturn(
                SandboxExecutionResult.failed(UUID.randomUUID(), proposal.getId(), 1,
                        Duration.ofMillis(100), "Compilation error: cannot find symbol", "javac failed", false, "COMPILATION_ERROR", "Exit code 1"));

        SandboxExecution execution = sandboxExecutionService.queueExecution(proposal.getId(), org.getId(), SandboxExecutionProfile.JAVA_MAVEN_TEST);
        execution = sandboxExecutionService.executeExecution(execution.getId());
        assertThat(execution.getStatus()).isEqualTo(SandboxExecutionStatus.FAILED);

        // Attempting to request approval on failed sandbox execution MUST be rejected
        assertThatThrownBy(() -> approvalService.requestApproval(owner.getId(), org.getId(), new RequestApprovalRequest(proposal.getId())))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo("NO_SUCCESSFUL_SANDBOX_EXECUTION");
                });

        assertThat(approvalRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Scenario D: Approval Rejection audits decision, is idempotent, and blocks GitHub mutation")
    void scenarioD_approvalRejectionFlow() {
        Task task = taskService.create(owner.getId(), org.getId(), new CreateTaskRequest(repo.getId(), "Rejection task", "Test rejection"));
        task = taskStateService.startTask(org.getId(), task.getId(), TaskActor.worker(), "start-d-" + task.getId());
        ProposedChange proposal = createTestProposal(task);

        when(sandboxExecutor.execute(any(), any())).thenReturn(
                SandboxExecutionResult.succeeded(UUID.randomUUID(), proposal.getId(), 0, Duration.ofMillis(50), "OK", "", false));
        SandboxExecution execution = sandboxExecutionService.queueExecution(proposal.getId(), org.getId(), SandboxExecutionProfile.JAVA_MAVEN_TEST);
        sandboxExecutionService.executeExecution(execution.getId());

        Approval approval = approvalService.requestApproval(owner.getId(), org.getId(), new RequestApprovalRequest(proposal.getId()));
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);

        // 1. Reject approval
        approval = approvalService.reject(owner.getId(), org.getId(), approval.getId(),
                new RejectApprovalRequest("Security review failed: unexpected reflection pattern"));
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.REJECTED);

        // 2. Duplicate rejection is idempotent
        Approval secondReject = approvalService.reject(owner.getId(), org.getId(), approval.getId(),
                new RejectApprovalRequest("Duplicate call"));
        assertThat(secondReject.getStatus()).isEqualTo(ApprovalStatus.REJECTED);

        UUID decidedApprovalId = approval.getId();
        UUID ownerId = owner.getId();
        UUID orgId = org.getId();
        // 3. Contradictory approval attempt throws 409 Conflict
        assertThatThrownBy(() -> approvalService.approve(ownerId, orgId, decidedApprovalId,
                new ApproveApprovalRequest("Trying to approve after reject")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        // 4. Mutation enqueue fails
        assertThatThrownBy(() -> githubMutationService.enqueueExecution(ownerId, orgId, decidedApprovalId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(gitClient);
        verifyNoInteractions(pullRequestClient);
    }

    @Test
    @DisplayName("Scenario E: GitHub Mutation Failure on stale base revision")
    void scenarioE_gitHubMutationStaleBaseRevisionFailure() {
        Task task = taskService.create(owner.getId(), org.getId(), new CreateTaskRequest(repo.getId(), "Stale base task", "Test stale base"));
        task = taskStateService.startTask(org.getId(), task.getId(), TaskActor.worker(), "start-e-" + task.getId());
        ProposedChange proposal = createTestProposal(task);

        when(sandboxExecutor.execute(any(), any())).thenReturn(
                SandboxExecutionResult.succeeded(UUID.randomUUID(), proposal.getId(), 0, Duration.ofMillis(50), "OK", "", false));
        SandboxExecution execution = sandboxExecutionService.queueExecution(proposal.getId(), org.getId(), SandboxExecutionProfile.JAVA_MAVEN_TEST);
        sandboxExecutionService.executeExecution(execution.getId());

        Approval approval = approvalService.requestApproval(owner.getId(), org.getId(), new RequestApprovalRequest(proposal.getId()));
        approval = approvalService.approve(owner.getId(), org.getId(), approval.getId(), new ApproveApprovalRequest("Approved"));

        var mutationResp = githubMutationService.enqueueExecution(owner.getId(), org.getId(), approval.getId());

        // Remote default branch has a different SHA than proposal's base revision
        when(pullRequestClient.findPullRequest(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(gitClient.getRef(eq("devflow"), eq("demo-service"), eq("heads/main"), anyString()))
                .thenReturn(Optional.of(new GitRef("refs/heads/main", "different-remote-base-sha-999")));

        githubMutationExecutor.execute(mutationResp.id());

        GithubMutation mutation = githubMutationRepository.findByIdWithDetails(mutationResp.id()).orElseThrow();
        assertThat(mutation.getStatus()).isEqualTo(GithubMutationStatus.FAILED);
        assertThat(mutation.getFailureCode()).isEqualTo("BASE_REVISION_STALE");
        assertThat(mutation.getPullRequestNumber()).isNull();
    }

    @Test
    @DisplayName("Scenario F: Task Cancellation across states and terminal state immutability")
    void scenarioF_taskCancellationAndImmutability() {
        // 1. Cancel in QUEUED
        Task taskQueued = taskService.create(owner.getId(), org.getId(), new CreateTaskRequest(repo.getId(), "Cancel in queued", "desc"));
        Task cancelledQueued = taskStateService.cancelTask(owner.getId(), org.getId(), taskQueued.getId(), "cancel-q-" + taskQueued.getId());
        assertThat(cancelledQueued.getStatus()).isEqualTo(TaskStatus.CANCELLED);

        // 2. Cancel in RUNNING
        Task taskRunning = taskService.create(owner.getId(), org.getId(), new CreateTaskRequest(repo.getId(), "Cancel in running", "desc"));
        taskRunning = taskStateService.startTask(org.getId(), taskRunning.getId(), TaskActor.worker(), "start-f1-" + taskRunning.getId());
        Task cancelledRunning = taskStateService.cancelTask(owner.getId(), org.getId(), taskRunning.getId(), "cancel-r-" + taskRunning.getId());
        assertThat(cancelledRunning.getStatus()).isEqualTo(TaskStatus.CANCELLED);

        // 3. Cancel in WAITING_FOR_APPROVAL
        Task taskWaiting = taskService.create(owner.getId(), org.getId(), new CreateTaskRequest(repo.getId(), "Cancel in waiting", "desc"));
        taskWaiting = taskStateService.startTask(org.getId(), taskWaiting.getId(), TaskActor.worker(), "start-f2-" + taskWaiting.getId());
        taskWaiting = taskStateService.requestApproval(org.getId(), taskWaiting.getId(), TaskActor.worker(), "req-f2-" + taskWaiting.getId());
        Task cancelledWaiting = taskStateService.cancelTask(owner.getId(), org.getId(), taskWaiting.getId(), "cancel-w-" + taskWaiting.getId());
        assertThat(cancelledWaiting.getStatus()).isEqualTo(TaskStatus.CANCELLED);

        // 4. Terminal state CANCELLED cannot transition to RUNNING
        UUID cancelledId = cancelledQueued.getId();
        assertThatThrownBy(() -> taskStateService.startTask(org.getId(), cancelledId, TaskActor.worker(), "illegal-start"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        // 5. Terminal state SUCCEEDED cannot be cancelled
        Task taskSuccess = taskService.create(owner.getId(), org.getId(), new CreateTaskRequest(repo.getId(), "Success task", "desc"));
        taskSuccess = taskStateService.startTask(org.getId(), taskSuccess.getId(), TaskActor.worker(), "start-f3-" + taskSuccess.getId());
        taskSuccess = taskStateService.succeedTask(org.getId(), taskSuccess.getId(), TaskActor.worker(), "succ-f3-" + taskSuccess.getId());
        UUID successId = taskSuccess.getId();
        assertThatThrownBy(() -> taskStateService.cancelTask(owner.getId(), org.getId(), successId, "illegal-cancel"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private ProposedChange createTestProposal(Task task) {
        String unifiedDiff = """
                diff --git a/src/App.java b/src/App.java
                new file mode 100644
                --- /dev/null
                +++ b/src/App.java
                @@ -0,0 +1,3 @@
                +public class App {
                +    // new app
                +}
                """;
        com.devflow.api.agent.domain.AgentExecutionContext agentCtx = new com.devflow.api.agent.domain.AgentExecutionContext(
                owner.getId(), org.getId(), task.getId(), repo.getId(), "Test change", repo.getFullName());

        ProposedChangeFileCommand fileCmd = new ProposedChangeFileCommand(
                "src/App.java",
                ProposedChangeOperation.ADD,
                3, 0,
                unifiedDiff
        );

        ProposedChangeCommand proposalCmd = new ProposedChangeCommand(
                "Test proposed change",
                "Description for test proposal",
                "1122334455667788990011223344556677889900",
                unifiedDiff,
                List.of(fileCmd)
        );

        return proposedChangeService.create(agentCtx, proposalCmd);
    }
}
