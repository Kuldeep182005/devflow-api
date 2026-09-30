package com.devflow.api.e2e;

import com.devflow.api.approval.api.ApproveApprovalRequest;
import com.devflow.api.approval.api.RejectApprovalRequest;
import com.devflow.api.approval.api.RequestApprovalRequest;
import com.devflow.api.approval.domain.Approval;
import com.devflow.api.approval.domain.ApprovalStatus;
import com.devflow.api.approval.service.ApprovalService;
import com.devflow.api.github.mutation.service.GithubMutationService;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.persistence.approval.ApprovalRepository;
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
import com.devflow.api.sandbox.executor.SandboxExecutionResult;
import com.devflow.api.sandbox.executor.SandboxExecutor;
import com.devflow.api.sandbox.service.SandboxExecutionService;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskActor;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.task.service.TaskService;
import com.devflow.api.task.service.TaskStateService;
import com.devflow.api.user.domain.User;
import com.devflow.api.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
class DevFlowMultiTenantSecurityE2ETest {

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
    private TaskService taskService;

    @Autowired
    private TaskStateService taskStateService;

    @Autowired
    private ProposedChangeService proposedChangeService;

    @Autowired
    private SandboxExecutionService sandboxExecutionService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private GithubMutationService githubMutationService;

    @MockBean
    private SandboxExecutor sandboxExecutor;

    @MockBean
    private com.devflow.api.llm.provider.LlmProvider llmProvider;

    private User userA;
    private Organization orgA;
    private Repository repoA;
    private Task taskA;
    private ProposedChange proposalA;
    private Approval approvalA;

    private User userB;
    private Organization orgB;
    private Repository repoB;
    private Task taskB;
    private ProposedChange proposalB;
    private Approval approvalB;

    private User viewerUser;

    @BeforeEach
    void setUp() {
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

        // Org A setup
        userA = userRepository.saveAndFlush(new User("usera-" + now.toEpochMilli() + "@a.com", "User A", "subA-" + now, true, now, now));
        orgA = organizationRepository.saveAndFlush(new Organization("Org A", "org-a-" + now.toEpochMilli(), now, now));
        memberRepository.saveAndFlush(new OrganizationMember(orgA, userA, OrganizationRole.OWNER, now));
        repoA = repositoryRepository.saveAndFlush(new Repository(orgA, RepositoryProvider.GITHUB, "org-a/repo", "org-a/repo", "main", true, now, now));
        repoA.associateGithubInstallation(111L);
        repositoryRepository.saveAndFlush(repoA);

        taskA = taskRepository.saveAndFlush(new Task(orgA, repoA, userA, com.devflow.api.task.domain.TaskSourceType.MANUAL, null,
                "Task A", "desc A", TaskStatus.RUNNING, null, null, null, now, now, null, null));
        proposalA = createTestProposal(userA, orgA, repoA, taskA, "Proposal A");

        // Viewer user in Org A
        viewerUser = userRepository.saveAndFlush(new User("viewer-" + now.toEpochMilli() + "@a.com", "Viewer User", "subV-" + now, true, now, now));
        memberRepository.saveAndFlush(new OrganizationMember(orgA, viewerUser, OrganizationRole.VIEWER, now));

        // Org B setup
        userB = userRepository.saveAndFlush(new User("userb-" + now.toEpochMilli() + "@b.com", "User B", "subB-" + now, true, now, now));
        orgB = organizationRepository.saveAndFlush(new Organization("Org B", "org-b-" + now.toEpochMilli(), now, now));
        memberRepository.saveAndFlush(new OrganizationMember(orgB, userB, OrganizationRole.OWNER, now));
        repoB = repositoryRepository.saveAndFlush(new Repository(orgB, RepositoryProvider.GITHUB, "org-b/repo", "org-b/repo", "main", true, now, now));
        repoB.associateGithubInstallation(222L);
        repositoryRepository.saveAndFlush(repoB);

        taskB = taskRepository.saveAndFlush(new Task(orgB, repoB, userB, com.devflow.api.task.domain.TaskSourceType.MANUAL, null,
                "Task B", "desc B", TaskStatus.RUNNING, null, null, null, now, now, null, null));
        proposalB = createTestProposal(userB, orgB, repoB, taskB, "Proposal B");

        when(sandboxExecutor.execute(any(), any())).thenReturn(
                SandboxExecutionResult.succeeded(UUID.randomUUID(), proposalA.getId(), 0, Duration.ofMillis(50), "OK", "", false));

        // Succeeded sandbox for Org A
        SandboxExecution execA = sandboxExecutionService.queueExecution(proposalA.getId(), orgA.getId(), SandboxExecutionProfile.JAVA_MAVEN_TEST);
        sandboxExecutionService.executeExecution(execA.getId());
        approvalA = approvalService.requestApproval(userA.getId(), orgA.getId(), new RequestApprovalRequest(proposalA.getId()));

        // Succeeded sandbox for Org B
        when(sandboxExecutor.execute(any(), any())).thenReturn(
                SandboxExecutionResult.succeeded(UUID.randomUUID(), proposalB.getId(), 0, Duration.ofMillis(50), "OK", "", false));
        SandboxExecution execB = sandboxExecutionService.queueExecution(proposalB.getId(), orgB.getId(), SandboxExecutionProfile.JAVA_MAVEN_TEST);
        sandboxExecutionService.executeExecution(execB.getId());
        approvalB = approvalService.requestApproval(userB.getId(), orgB.getId(), new RequestApprovalRequest(proposalB.getId()));
    }

    @Test
    @DisplayName("Cross-tenant isolation: User A cannot access or cancel Task B")
    void testTenantIsolation_taskAccess() {
        UUID userAId = userA.getId();
        UUID orgAId = orgA.getId();
        UUID taskBId = taskB.getId();

        assertThatThrownBy(() -> taskStateService.cancelTask(userAId, orgAId, taskBId, "cancel-cross"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("TASK_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("Cross-tenant isolation: User A cannot read or mutate ProposedChange B")
    void testTenantIsolation_proposalAccess() {
        UUID orgAId = orgA.getId();
        UUID proposalBId = proposalB.getId();

        assertThatThrownBy(() -> proposedChangeService.getById(proposalBId, orgAId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Cross-tenant isolation: User A cannot queue Sandbox Execution for ProposedChange B")
    void testTenantIsolation_sandboxQueue() {
        UUID orgAId = orgA.getId();
        UUID proposalBId = proposalB.getId();

        assertThatThrownBy(() -> sandboxExecutionService.queueExecution(proposalBId, orgAId, SandboxExecutionProfile.JAVA_MAVEN_TEST))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Cross-tenant isolation: User A cannot request or decide Approval B")
    void testTenantIsolation_approvalAccess() {
        UUID userAId = userA.getId();
        UUID orgAId = orgA.getId();
        UUID approvalBId = approvalB.getId();

        // User A cannot approve Approval B
        assertThatThrownBy(() -> approvalService.approve(userAId, orgAId, approvalBId, new ApproveApprovalRequest("Sneaky approve")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("APPROVAL_NOT_FOUND");
                });

        // User A cannot reject Approval B
        assertThatThrownBy(() -> approvalService.reject(userAId, orgAId, approvalBId, new RejectApprovalRequest("Sneaky reject")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("APPROVAL_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("Cross-tenant isolation: User A cannot enqueue GitHub Mutation for Approval B")
    void testTenantIsolation_githubMutation() {
        UUID userAId = userA.getId();
        UUID orgAId = orgA.getId();
        UUID approvalBId = approvalB.getId();

        assertThatThrownBy(() -> githubMutationService.enqueueExecution(userAId, orgAId, approvalBId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("APPROVAL_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("Role RBAC: VIEWER role cannot cancel tasks, request approval, approve, or execute mutation")
    void testRbacRestrictions_viewerRole() {
        UUID viewerId = viewerUser.getId();
        UUID orgAId = orgA.getId();
        UUID taskAId = taskA.getId();
        UUID proposalAId = proposalA.getId();
        UUID approvalAId = approvalA.getId();

        // 1. VIEWER cannot cancel task
        assertThatThrownBy(() -> taskStateService.cancelTask(viewerId, orgAId, taskAId, "cancel-viewer"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("TASK_CANCEL_FORBIDDEN");
                });

        // 2. VIEWER cannot request approval
        assertThatThrownBy(() -> approvalService.requestApproval(viewerId, orgAId, new RequestApprovalRequest(proposalAId)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("FORBIDDEN");
                });

        // 3. VIEWER cannot approve
        assertThatThrownBy(() -> approvalService.approve(viewerId, orgAId, approvalAId, new ApproveApprovalRequest("Viewer approve")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("FORBIDDEN");
                });

        // 4. VIEWER cannot enqueue mutation
        approvalService.approve(userA.getId(), orgAId, approvalAId, new ApproveApprovalRequest("Owner approved"));
        assertThatThrownBy(() -> githubMutationService.enqueueExecution(viewerId, orgAId, approvalAId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("Removed/Inactive membership: actions are rejected with safe 404")
    void testInactiveMembershipRejection() {
        // Remove userA from orgA
        memberRepository.deleteById(new com.devflow.api.organization.domain.OrganizationMemberId(orgA.getId(), userA.getId()));

        UUID userAId = userA.getId();
        UUID orgAId = orgA.getId();
        UUID taskAId = taskA.getId();

        assertThatThrownBy(() -> taskStateService.cancelTask(userAId, orgAId, taskAId, "cancel-removed"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("ORGANIZATION_NOT_FOUND");
                });
    }

    private ProposedChange createTestProposal(User user, Organization org, Repository repo, Task task, String title) {
        String unifiedDiff = """
                diff --git a/src/File.java b/src/File.java
                new file mode 100644
                --- /dev/null
                +++ b/src/File.java
                @@ -0,0 +1,3 @@
                +public class File {
                +    // new file
                +}
                """;

        com.devflow.api.agent.domain.AgentExecutionContext agentCtx = new com.devflow.api.agent.domain.AgentExecutionContext(
                user.getId(), org.getId(), task.getId(), repo.getId(), title, repo.getFullName());

        ProposedChangeFileCommand fileCmd = new ProposedChangeFileCommand(
                "src/File.java",
                ProposedChangeOperation.ADD,
                3, 0,
                unifiedDiff
        );

        ProposedChangeCommand proposalCmd = new ProposedChangeCommand(
                title,
                "Description for " + title,
                "1122334455667788990011223344556677889900",
                unifiedDiff,
                List.of(fileCmd)
        );

        return proposedChangeService.create(agentCtx, proposalCmd);
    }
}
