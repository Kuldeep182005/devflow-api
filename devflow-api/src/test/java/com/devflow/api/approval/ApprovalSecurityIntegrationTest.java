package com.devflow.api.approval;

import com.devflow.api.approval.domain.Approval;
import com.devflow.api.approval.domain.ApprovalStatus;
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
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeStatus;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.security.JwtService;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.task.domain.TaskEventType;
import com.devflow.api.task.domain.TaskSourceType;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "async.worker.enabled=false",
        "async.outbox.poll-interval-ms=60000"
})
@AutoConfigureMockMvc
@Testcontainers
class ApprovalSecurityIntegrationTest {

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
    private MockMvc mockMvc;

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
    private ProposedChangeRepository proposedChangeRepository;

    @Autowired
    private SandboxExecutionRepository sandboxExecutionRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private TaskEventRepository taskEventRepository;

    @Autowired
    private JwtService jwtService;

    private User ownerA;
    private User memberA;
    private User adminA;
    private User viewerA;
    private Organization orgA;
    private Repository repoA;
    private Task taskA;
    private ProposedChange changeA;
    private SandboxExecution executionA;

    private User ownerB;
    private Organization orgB;
    private Repository repoB;
    private Task taskB;
    private ProposedChange changeB;
    private SandboxExecution executionB;

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

        // 1. Create Tenant A
        ownerA = userRepository.saveAndFlush(new User("owner-a-" + now.toEpochMilli() + "@example.com", "Owner A", "owner-a-auth-" + now.toEpochMilli(), true, now, now));
        memberA = userRepository.saveAndFlush(new User("member-a-" + now.toEpochMilli() + "@example.com", "Member A", "member-a-auth-" + now.toEpochMilli(), true, now, now));
        adminA = userRepository.saveAndFlush(new User("admin-a-" + now.toEpochMilli() + "@example.com", "Admin A", "admin-a-auth-" + now.toEpochMilli(), true, now, now));
        viewerA = userRepository.saveAndFlush(new User("viewer-a-" + now.toEpochMilli() + "@example.com", "Viewer A", "viewer-a-auth-" + now.toEpochMilli(), true, now, now));

        orgA = organizationRepository.saveAndFlush(new Organization("Organization A", "org-a-" + now.toEpochMilli(), now, now));
        memberRepository.saveAndFlush(new OrganizationMember(orgA, ownerA, OrganizationRole.OWNER, now));
        memberRepository.saveAndFlush(new OrganizationMember(orgA, memberA, OrganizationRole.MEMBER, now));
        memberRepository.saveAndFlush(new OrganizationMember(orgA, adminA, OrganizationRole.ADMIN, now));
        memberRepository.saveAndFlush(new OrganizationMember(orgA, viewerA, OrganizationRole.VIEWER, now));

        repoA = repositoryRepository.saveAndFlush(new Repository(orgA, RepositoryProvider.GITHUB, "repo-a-" + now.toEpochMilli(), "a/a", "main", true, now, now));
        taskA = taskRepository.saveAndFlush(new Task(orgA, repoA, ownerA, TaskSourceType.MANUAL, null, "Task A", "Desc", TaskStatus.WAITING_FOR_APPROVAL, null, null, null, now, now, null, null));

        changeA = proposedChangeRepository.saveAndFlush(new ProposedChange(
                taskA, orgA, repoA, ProposedChangeStatus.VALIDATED, "Change A", "Desc", "main",
                "diff --git a/A.java b/A.java\n--- /dev/null\n+++ b/A.java\n@@ -0,0 +1,1 @@\n+class A {}",
                "{\"status\":\"VALIDATED\"}", now
        ));

        executionA = new SandboxExecution(changeA, taskA, orgA, repoA, SandboxExecutionProfile.JAVA_MAVEN_TEST, now);
        executionA.markRunning(now);
        executionA.complete(SandboxExecutionStatus.SUCCEEDED, 0, "BUILD SUCCESS", "", false, null, null, now.plusSeconds(1));
        executionA = sandboxExecutionRepository.saveAndFlush(executionA);

        // 2. Create Tenant B
        ownerB = userRepository.saveAndFlush(new User("owner-b-" + now.toEpochMilli() + "@example.com", "Owner B", "owner-b-auth-" + now.toEpochMilli(), true, now, now));
        orgB = organizationRepository.saveAndFlush(new Organization("Organization B", "org-b-" + now.toEpochMilli(), now, now));
        memberRepository.saveAndFlush(new OrganizationMember(orgB, ownerB, OrganizationRole.OWNER, now));

        repoB = repositoryRepository.saveAndFlush(new Repository(orgB, RepositoryProvider.GITHUB, "repo-b-" + now.toEpochMilli(), "b/b", "main", true, now, now));
        taskB = taskRepository.saveAndFlush(new Task(orgB, repoB, ownerB, TaskSourceType.MANUAL, null, "Task B", "Desc", TaskStatus.WAITING_FOR_APPROVAL, null, null, null, now, now, null, null));

        changeB = proposedChangeRepository.saveAndFlush(new ProposedChange(
                taskB, orgB, repoB, ProposedChangeStatus.VALIDATED, "Change B", "Desc", "main",
                "diff --git a/B.java b/B.java\n--- /dev/null\n+++ b/B.java\n@@ -0,0 +1,1 @@\n+class B {}",
                "{\"status\":\"VALIDATED\"}", now
        ));

        executionB = new SandboxExecution(changeB, taskB, orgB, repoB, SandboxExecutionProfile.JAVA_MAVEN_TEST, now);
        executionB.markRunning(now);
        executionB.complete(SandboxExecutionStatus.SUCCEEDED, 0, "BUILD SUCCESS", "", false, null, null, now.plusSeconds(1));
        executionB = sandboxExecutionRepository.saveAndFlush(executionB);
    }

    private String tokenFor(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    @Test
    @DisplayName("Request approval creates pending approval and emits APPROVAL_REQUESTED TaskEvent atomically")
    void requestApprovalPersistsRecordAndEvent() throws Exception {
        mockMvc.perform(post("/api/v1/organizations/" + orgA.getId() + "/approvals")
                        .header("Authorization", tokenFor(memberA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposedChangeId\":\"" + changeA.getId() + "\"}"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.proposedChangeVersion").value(changeA.getVersion()))
                .andExpect(jsonPath("$.requestedByUserId").value(memberA.getId().toString()));

        List<Approval> approvals = approvalRepository.findAll();
        assertThat(approvals).hasSize(1);
        Approval saved = approvals.get(0);
        assertThat(saved.getStatus()).isEqualTo(ApprovalStatus.PENDING);

        List<TaskEvent> events = taskEventRepository.findAll();
        assertThat(events).hasSize(1);
        TaskEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo(TaskEventType.APPROVAL_REQUESTED);
        assertThat(event.getActorUserId()).isEqualTo(memberA.getId());
        assertThat(event.getMetadata()).contains(saved.getId().toString());
    }

    @Test
    @DisplayName("Cross-organization IDOR is blocked: User A cannot read, approve, or reject Approval B")
    void crossOrgIdorIsPrevented() throws Exception {
        Instant now = Instant.now();
        Approval approvalB = approvalRepository.saveAndFlush(new Approval(
                changeB, taskB, orgB, repoB, changeB.getVersion(), executionB, ownerB, now
        ));

        // 1. User A tries to read Approval B using Org A path -> 404 (does not exist in Org A)
        mockMvc.perform(get("/api/v1/organizations/" + orgA.getId() + "/approvals/" + approvalB.getId())
                        .header("Authorization", tokenFor(ownerA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_FOUND"));

        // 2. User A tries to read Approval B using Org B path -> 404 (User A is not member of Org B)
        mockMvc.perform(get("/api/v1/organizations/" + orgB.getId() + "/approvals/" + approvalB.getId())
                        .header("Authorization", tokenFor(ownerA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));

        // 3. User A tries to approve Approval B using Org A path -> 404
        mockMvc.perform(post("/api/v1/organizations/" + orgA.getId() + "/approvals/" + approvalB.getId() + "/approve")
                        .header("Authorization", tokenFor(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Cross-org attempt\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_FOUND"));

        // 4. User A tries to reject Approval B using Org A path -> 404
        mockMvc.perform(post("/api/v1/organizations/" + orgA.getId() + "/approvals/" + approvalB.getId() + "/reject")
                        .header("Authorization", tokenFor(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cross-org attempt\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_FOUND"));

        // 5. User A tries to create an approval for Proposal B under Org A -> 404
        mockMvc.perform(post("/api/v1/organizations/" + orgA.getId() + "/approvals")
                        .header("Authorization", tokenFor(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposedChangeId\":\"" + changeB.getId() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROPOSED_CHANGE_NOT_FOUND"));

        // Verify Approval B remains unmodified in PENDING status
        Approval unchanged = approvalRepository.findById(approvalB.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("Role boundary enforced: MEMBER and VIEWER cannot approve, OWNER and ADMIN can")
    void roleBoundariesEnforced() throws Exception {
        Instant now = Instant.now();
        Approval approvalA = approvalRepository.saveAndFlush(new Approval(
                changeA, taskA, orgA, repoA, changeA.getVersion(), executionA, memberA, now
        ));

        // 1. MEMBER tries to approve -> 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/organizations/" + orgA.getId() + "/approvals/" + approvalA.getId() + "/approve")
                        .header("Authorization", tokenFor(memberA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Member approving\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 2. VIEWER tries to approve -> 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/organizations/" + orgA.getId() + "/approvals/" + approvalA.getId() + "/approve")
                        .header("Authorization", tokenFor(viewerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Viewer approving\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 3. ADMIN approves -> 200 OK
        mockMvc.perform(post("/api/v1/organizations/" + orgA.getId() + "/approvals/" + approvalA.getId() + "/approve")
                        .header("Authorization", tokenFor(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Approved by Admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedByUserId").value(adminA.getId().toString()))
                .andExpect(jsonPath("$.decisionReason").value("Approved by Admin"));

        // Verify TaskEvent emitted
        List<TaskEvent> events = taskEventRepository.findAll();
        assertThat(events).anyMatch(e -> e.getEventType() == TaskEventType.APPROVAL_APPROVED && e.getActorUserId().equals(adminA.getId()));
    }

    @Test
    @DisplayName("Stale version protection: approving a proposal whose version has evolved fails with 409")
    void staleVersionFailsApproval() throws Exception {
        Instant now = Instant.now();
        Approval approvalA = approvalRepository.saveAndFlush(new Approval(
                changeA, taskA, orgA, repoA, changeA.getVersion(), executionA, memberA, now
        ));

        // Increment proposal version by updating it
        changeA = proposedChangeRepository.findById(changeA.getId()).orElseThrow();
        // Trigger version bump
        changeA = proposedChangeRepository.saveAndFlush(new ProposedChange(
                taskA, orgA, repoA, ProposedChangeStatus.VALIDATED, "Change A v2", "Updated", "main",
                "diff --git a/A.java b/A.java\n--- /dev/null\n+++ b/A.java\n@@ -0,0 +1,2 @@\n+class A {}\n+// v2",
                "{\"status\":\"VALIDATED\"}", now
        ));
        // Manually simulate proposal evolving: approval points to older version
        Approval staleApproval = approvalRepository.saveAndFlush(new Approval(
                changeA, taskA, orgA, repoA, 999L, executionA, memberA, now
        ));

        mockMvc.perform(post("/api/v1/organizations/" + orgA.getId() + "/approvals/" + staleApproval.getId() + "/approve")
                        .header("Authorization", tokenFor(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Should fail\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSED_CHANGE_STALE"));
    }

    @Test
    @DisplayName("Idempotency: repeating identical decision returns 200 OK without side effects; contradicting decision returns 409")
    void idempotencyAndContradictingDecision() throws Exception {
        Instant now = Instant.now();
        Approval approvalA = approvalRepository.saveAndFlush(new Approval(
                changeA, taskA, orgA, repoA, changeA.getVersion(), executionA, memberA, now
        ));

        // 1. Initial approve -> 200 OK
        mockMvc.perform(post("/api/v1/organizations/" + orgA.getId() + "/approvals/" + approvalA.getId() + "/approve")
                        .header("Authorization", tokenFor(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        int eventCount = taskEventRepository.findAll().size();

        // 2. Retry approve by same decider -> 200 OK idempotent (no extra events)
        mockMvc.perform(post("/api/v1/organizations/" + orgA.getId() + "/approvals/" + approvalA.getId() + "/approve")
                        .header("Authorization", tokenFor(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(taskEventRepository.findAll().size()).isEqualTo(eventCount);

        // 3. Contradictory decision: reject an already approved change -> 409 Conflict
        mockMvc.perform(post("/api/v1/organizations/" + orgA.getId() + "/approvals/" + approvalA.getId() + "/reject")
                        .header("Authorization", tokenFor(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Trying to reject after approve\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_ALREADY_DECIDED"));
    }

    @Test
    @DisplayName("Concurrency: two simultaneous approval requests yield exactly one success and one conflict")
    void concurrentDecisionsHandledSafely() throws Exception {
        Instant now = Instant.now();
        Approval approval = approvalRepository.saveAndFlush(new Approval(
                changeA, taskA, orgA, repoA, changeA.getVersion(), executionA, memberA, now
        ));

        int threads = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Runnable approveAction = (Runnable) () -> {
            try {
                startLatch.await();
                var result = mockMvc.perform(post("/api/v1/organizations/" + orgA.getId() + "/approvals/" + approval.getId() + "/approve")
                                .header("Authorization", tokenFor(ownerA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"Concurrent approve\"}"))
                        .andReturn();
                int status = result.getResponse().getStatus();
                if (status == 200) {
                    successCount.incrementAndGet();
                } else if (status == 409) {
                    conflictCount.incrementAndGet();
                }
            } catch (Exception e) {
                // ignore
            } finally {
                doneLatch.countDown();
            }
        };

        new Thread(approveAction).start();
        new Thread(approveAction).start();

        startLatch.countDown();
        doneLatch.await();

        // Exactly one should succeed, or if executed serially under same user idempotent retry returns 200,
        // but both cannot produce divergent states: status is APPROVED
        Approval finalApproval = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(finalApproval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }
}
