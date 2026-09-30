package com.devflow.api.e2e;

import com.devflow.api.agent.domain.AgentDecision;
import com.devflow.api.agent.exception.InvalidAgentDecisionException;
import com.devflow.api.agent.validation.AgentDecisionParser;
import com.devflow.api.approval.api.ApproveApprovalRequest;
import com.devflow.api.approval.api.RejectApprovalRequest;
import com.devflow.api.approval.domain.Approval;
import com.devflow.api.approval.service.ApprovalService;
import com.devflow.api.async.persistence.AsyncJobStore;
import com.devflow.api.async.persistence.AsyncJobStore.JobState;
import com.devflow.api.async.persistence.AsyncJobStore.JobStatus;
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
import com.devflow.api.proposedchange.exception.ProposedChangeException;
import com.devflow.api.proposedchange.service.ProposedChangeService;
import com.devflow.api.proposedchange.validation.ProposedChangePathValidator;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.repositorytool.domain.RepositoryWorkspace;
import com.devflow.api.repositorytool.exception.RepositoryToolException;
import com.devflow.api.repositorytool.security.PathSecurity;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import com.devflow.api.sandbox.executor.SandboxExecutionResult;
import com.devflow.api.sandbox.executor.SandboxExecutor;
import com.devflow.api.sandbox.service.SandboxExecutionService;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.user.domain.User;
import com.devflow.api.web.ApiException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
class DevFlowReliabilityAndIdempotencyE2ETest {

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
    private ProposedChangeService proposedChangeService;

    @Autowired
    private SandboxExecutionService sandboxExecutionService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private GithubMutationService githubMutationService;

    @Autowired
    private GithubMutationExecutor githubMutationExecutor;

    @Autowired
    private AsyncJobStore asyncJobStore;

    @Autowired
    private AgentDecisionParser decisionParser;

    @Autowired
    private ProposedChangePathValidator pathValidator;

    @MockBean
    private SandboxExecutor sandboxExecutor;

    @MockBean
    private com.devflow.api.llm.provider.LlmProvider llmProvider;

    @MockBean
    private GithubIntegrationService githubIntegrationService;

    @MockBean
    private GithubGitClient gitClient;

    @MockBean
    private GithubPullRequestClient pullRequestClient;

    private User owner;
    private Organization org;
    private Repository repo;
    private Task task;
    private ProposedChange proposal;
    private Approval approval;

    @BeforeEach
    void setUp() {
        githubMutationRepository.deleteAll();
        approvalRepository.deleteAll();
        sandboxExecutionRepository.deleteAll();
        proposedChangeRepository.deleteAll();
        taskEventRepository.deleteAll();
        taskRepository.deleteAll();
        repositoryRepository.deleteAll();
        memberRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();

        Instant now = Instant.now();
        owner = userRepository.saveAndFlush(new User("owner-" + now.toEpochMilli() + "@devflow.io",
                "Owner", "auth0|" + now.toEpochMilli(), true, now, now));
        org = organizationRepository.saveAndFlush(new Organization("Acme Corp", "acme-" + now.toEpochMilli(), now, now));
        memberRepository.saveAndFlush(new OrganizationMember(org, owner, OrganizationRole.OWNER, now));

        repo = repositoryRepository.saveAndFlush(new Repository(org, RepositoryProvider.GITHUB, "acme/service",
                "acme/service", "main", true, now, now));
        repo.associateGithubInstallation(777L);
        repositoryRepository.saveAndFlush(repo);

        task = taskRepository.saveAndFlush(new Task(org, repo, owner, com.devflow.api.task.domain.TaskSourceType.MANUAL, null,
                "Reliability Task", "desc", TaskStatus.RUNNING, null, null, null, now, now, null, null));

        String diff = """
                diff --git a/src/Service.java b/src/Service.java
                new file mode 100644
                --- /dev/null
                +++ b/src/Service.java
                @@ -0,0 +1,3 @@
                +public class Service {
                +    // robust
                +}
                """;

        com.devflow.api.agent.domain.AgentExecutionContext ctx = new com.devflow.api.agent.domain.AgentExecutionContext(
                owner.getId(), org.getId(), task.getId(), repo.getId(), "Service update", repo.getFullName());
        proposal = proposedChangeService.create(ctx, new ProposedChangeCommand(
                "Service update", "Robustness patch", "0123456789abcdef0123456789abcdef01234567", diff,
                List.of(new ProposedChangeFileCommand("src/Service.java", ProposedChangeOperation.ADD, 3, 0, diff))));

        when(sandboxExecutor.execute(any(), any())).thenReturn(
                SandboxExecutionResult.succeeded(UUID.randomUUID(), proposal.getId(), 0, Duration.ofMillis(50), "OK", "", false));
        SandboxExecution exec = sandboxExecutionService.queueExecution(proposal.getId(), org.getId(), SandboxExecutionProfile.JAVA_MAVEN_TEST);
        sandboxExecutionService.executeExecution(exec.getId());

        approval = approvalService.requestApproval(owner.getId(), org.getId(),
                new com.devflow.api.approval.api.RequestApprovalRequest(proposal.getId()));

        when(githubIntegrationService.getInstallationToken(anyLong())).thenReturn("ghs_token_abc");
    }

    @Test
    @DisplayName("Worker Claim Locking: Concurrent workers cannot claim the same job simultaneously")
    void testWorkerClaimLocking() {
        UUID executionId = UUID.randomUUID();
        UUID jobId = asyncJobStore.enqueueSandboxExecution(executionId);

        // Worker 1 claims job
        JobState claim1 = asyncJobStore.claimJob(jobId);
        assertThat(claim1.claimed()).isTrue();
        assertThat(claim1.status()).isEqualTo(JobStatus.PROCESSING);

        // Worker 2 attempts to claim same job before it finishes or times out -> must fail
        JobState claim2 = asyncJobStore.claimJob(jobId);
        assertThat(claim2.claimed()).isFalse();
        assertThat(claim2.status()).isEqualTo(JobStatus.PROCESSING);
    }

    @Test
    @DisplayName("GitHub Mutation Idempotency & Reconciliation on duplicate delivery")
    void testGithubMutationDuplicateDeliveryReconciliation() {
        approval = approvalService.approve(owner.getId(), org.getId(), approval.getId(), new ApproveApprovalRequest("Approved"));
        var mutationResp = githubMutationService.enqueueExecution(owner.getId(), org.getId(), approval.getId());
        UUID mutationId = mutationResp.id();

        String baseSha = proposal.getBaseRevision();

        // 1. First execution succeeds and creates PR #42
        when(pullRequestClient.findPullRequest(eq("acme"), eq("service"), anyString(), eq("main"), anyString()))
                .thenReturn(Optional.empty());
        when(gitClient.getRef(eq("acme"), eq("service"), eq("heads/main"), anyString()))
                .thenReturn(Optional.of(new GitRef("refs/heads/main", baseSha)));
        when(gitClient.getRef(eq("acme"), eq("service"), startsWith("heads/devflow/"), anyString()))
                .thenReturn(Optional.empty());
        when(gitClient.getCommit(eq("acme"), eq("service"), eq(baseSha), anyString()))
                .thenReturn(Optional.of(new GitCommit(baseSha, "tree-111")));
        when(gitClient.getFileContent(eq("acme"), eq("service"), eq("src/Service.java"), eq(baseSha), anyString()))
                .thenReturn(Optional.empty());
        when(gitClient.createBlob(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("blob-111");
        when(gitClient.createTree(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn("tree-222");
        when(gitClient.createCommit(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn("commit-333");
        when(gitClient.createRef(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new GitRef("refs/heads/devflow/task-" + task.getId(), "commit-333"));
        when(pullRequestClient.createPullRequest(anyString(), anyString(), anyString(), anyString(), anyString(), eq("main"), anyString()))
                .thenReturn(new PullRequestResult(42, "https://github.com/acme/service/pull/42", "open"));

        githubMutationExecutor.execute(mutationId);

        GithubMutation mutation = githubMutationRepository.findByIdWithDetails(mutationId).orElseThrow();
        assertThat(mutation.getStatus()).isEqualTo(GithubMutationStatus.SUCCEEDED);
        assertThat(mutation.getPullRequestNumber()).isEqualTo(42);

        // 2. Duplicate redelivery of the same mutation job
        // The executor checks: mutation is already SUCCEEDED -> returns immediately without extra external calls
        githubMutationExecutor.execute(mutationId);

        // Verify createPullRequest was called exactly ONCE across both deliveries
        verify(pullRequestClient, times(1))
                .createPullRequest(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Hostile Repository Content & LLM Security: Disallowed tools and path traversal are rejected")
    void testSecurityAdversarialInputRejection() {
        // 1. LLM decision parser strictly rejects unallowlisted tools (e.g. SHELL_EXEC)
        String maliciousDecision = """
                {
                  "action": "CALL_TOOL",
                  "reasoningSummary": "Attempting shell escape",
                  "toolRequest": {
                    "tool": "SHELL_EXEC",
                    "arguments": {
                      "cmd": "rm -rf /"
                    }
                  }
                }
                """;
        assertThatThrownBy(() -> decisionParser.parse(maliciousDecision, 500, 1000))
                .isInstanceOf(InvalidAgentDecisionException.class);

        // 2. ProposedChangePathValidator strictly rejects path traversal
        assertThatThrownBy(() -> pathValidator.validate("../../../etc/passwd"))
                .isInstanceOf(ProposedChangeException.class);
        assertThatThrownBy(() -> pathValidator.validate("/etc/shadow"))
                .isInstanceOf(ProposedChangeException.class);
        assertThatThrownBy(() -> pathValidator.validate("src/../../secret.key"))
                .isInstanceOf(ProposedChangeException.class);

        // 3. PathSecurity strictly rejects paths outside repository root
        PathSecurity pathSecurity = new PathSecurity();
        RepositoryWorkspace workspace = new RepositoryWorkspace("test-repo", Path.of("temp-root").toAbsolutePath(), "main");
        assertThatThrownBy(() -> pathSecurity.resolve(workspace, "../../root/.ssh/id_rsa", 10, false))
                .isInstanceOf(RepositoryToolException.class)
                .satisfies(ex -> assertThat(((RepositoryToolException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
