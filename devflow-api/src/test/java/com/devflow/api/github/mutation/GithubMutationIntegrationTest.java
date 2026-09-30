package com.devflow.api.github.mutation;

import com.devflow.api.approval.domain.Approval;
import com.devflow.api.async.config.AsyncJobConfiguration;
import com.devflow.api.async.persistence.AsyncJobStore;
import com.devflow.api.github.config.GithubProperties;
import com.devflow.api.github.integration.GithubHttpClient;
import com.devflow.api.github.integration.GithubIntegrationService;
import com.devflow.api.github.mutation.api.GithubMutationResponse;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        AsyncJobConfiguration.class,
        AsyncJobStore.class,
        GithubMutationService.class,
        GithubMutationIntegrationTest.TestConfig.class
})
class GithubMutationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static HttpServer fakeGithubServer;
    private static int fakeGithubPort;

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        GithubHttpClient githubHttpClient(ObjectMapper objectMapper) {
            GithubProperties properties = new GithubProperties();
            properties.setBaseUrl("http://127.0.0.1:" + fakeGithubPort);
            return new GithubHttpClient(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                    properties,
                    objectMapper
            );
        }

        @Bean
        GithubMutationExecutor githubMutationExecutor(
                GithubMutationRepository mutationRepository,
                GithubIntegrationService githubIntegrationService,
                GithubHttpClient githubHttpClient,
                TaskEventRepository taskEventRepository,
                PlatformTransactionManager transactionManager) {
            return new GithubMutationExecutor(
                    mutationRepository,
                    githubIntegrationService,
                    githubHttpClient,
                    githubHttpClient,
                    taskEventRepository,
                    new TransactionTemplate(transactionManager)
            );
        }
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
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
    private GithubMutationRepository githubMutationRepository;
    @Autowired
    private TaskEventRepository taskEventRepository;
    @Autowired
    private GithubMutationService githubMutationService;
    @Autowired
    private GithubMutationExecutor githubMutationExecutor;

    @MockBean
    private GithubIntegrationService githubIntegrationService;

    @org.junit.jupiter.api.BeforeAll
    static void startServer() throws Exception {
        fakeGithubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeGithubPort = fakeGithubServer.getAddress().getPort();
        fakeGithubServer.start();
    }

    @org.junit.jupiter.api.AfterAll
    static void stopServer() {
        if (fakeGithubServer != null) {
            fakeGithubServer.stop(0);
        }
    }

    @BeforeEach
    void setupMock() {
        when(githubIntegrationService.getInstallationToken(anyLong())).thenReturn("fake-installation-token");
    }

    @AfterEach
    void cleanupContext() {
        if (fakeGithubServer != null) {
            try {
                fakeGithubServer.removeContext("/repos/acme/my-repo");
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Test
    @DisplayName("End-to-end GitHub mutation pipeline succeeds: branch -> commit -> PR -> audit events")
    void testEndToEndSuccessfulMutation() {
        TestData data = createTestData("commit-base-sha-1");

        fakeGithubServer.createContext("/repos/acme/my-repo", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (path.endsWith("/pulls")) {
                if ("GET".equalsIgnoreCase(method)) {
                    // No existing PR
                    sendJsonResponse(exchange, 200, "[]");
                } else if ("POST".equalsIgnoreCase(method)) {
                    // Create PR
                    sendJsonResponse(exchange, 201, """
                            {
                              "number": 101,
                              "html_url": "https://github.com/acme/my-repo/pull/101",
                              "state": "open"
                            }
                            """);
                }
            } else if (path.contains("/git/ref/heads/main")) {
                sendJsonResponse(exchange, 200, """
                        {
                          "ref": "refs/heads/main",
                          "object": {
                            "sha": "commit-base-sha-1"
                          }
                        }
                        """);
            } else if (path.contains("/git/ref/heads/devflow/")) {
                // Branch does not exist yet
                sendJsonResponse(exchange, 404, "{\"message\":\"Not Found\"}");
            } else if (path.contains("/contents/Hello.java")) {
                // File does not exist yet
                sendJsonResponse(exchange, 404, "{\"message\":\"Not Found\"}");
            } else if (path.contains("/git/commits/commit-base-sha-1")) {
                sendJsonResponse(exchange, 200, """
                        {
                          "sha": "commit-base-sha-1",
                          "tree": {
                            "sha": "tree-base-sha-1"
                          }
                        }
                        """);
            } else if (path.endsWith("/git/blobs")) {
                sendJsonResponse(exchange, 201, """
                        {
                          "sha": "blob-new-sha-1"
                        }
                        """);
            } else if (path.endsWith("/git/trees")) {
                sendJsonResponse(exchange, 201, """
                        {
                          "sha": "tree-new-sha-1"
                        }
                        """);
            } else if (path.endsWith("/git/commits")) {
                sendJsonResponse(exchange, 201, """
                        {
                          "sha": "commit-new-sha-1",
                          "tree": {
                            "sha": "tree-new-sha-1"
                          }
                        }
                        """);
            } else if (path.endsWith("/git/refs")) {
                sendJsonResponse(exchange, 201, """
                        {
                          "ref": "refs/heads/devflow/task-1/change-1",
                          "object": {
                            "sha": "commit-new-sha-1"
                          }
                        }
                        """);
            } else {
                sendJsonResponse(exchange, 404, "{\"message\":\"Not Found\"}");
            }
        });

        // 1. Enqueue execution via service
        GithubMutationResponse response = githubMutationService.enqueueExecution(
                data.adminUser.getId(),
                data.organization.getId(),
                data.approval.getId()
        );

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("QUEUED");

        // 2. Execute mutation
        githubMutationExecutor.execute(response.id());

        // 3. Verify mutation entity state in DB
        GithubMutation updated = githubMutationRepository.findById(response.id()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(GithubMutationStatus.SUCCEEDED);
        assertThat(updated.getCommitSha()).isEqualTo("commit-new-sha-1");
        assertThat(updated.getPullRequestNumber()).isEqualTo(101L);
        assertThat(updated.getPullRequestUrl()).isEqualTo("https://github.com/acme/my-repo/pull/101");
        assertThat(updated.getFailureCode()).isNull();

        // 4. Verify audit events in TaskEvent table
        List<TaskEvent> events = taskEventRepository.findAllByTaskIdAndOrganizationId(
                data.task.getId(), data.organization.getId(), PageRequest.of(0, 50)).getContent();
        List<TaskEventType> eventTypes = events.stream().map(TaskEvent::getEventType).toList();
        assertThat(eventTypes).contains(
                TaskEventType.GITHUB_COMMIT_CREATED,
                TaskEventType.GITHUB_BRANCH_CREATED,
                TaskEventType.GITHUB_PR_CREATED
        );
    }

    @Test
    @DisplayName("Fails safely with BASE_REVISION_STALE when GitHub base SHA does not match approved base revision")
    void testStaleBaseRevisionFailure() {
        TestData data = createTestData("expected-base-sha");

        fakeGithubServer.createContext("/repos/acme/my-repo", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/pulls")) {
                sendJsonResponse(exchange, 200, "[]");
            } else if (path.contains("/git/ref/heads/main")) {
                // Different SHA than expected-base-sha!
                sendJsonResponse(exchange, 200, """
                        {
                          "ref": "refs/heads/main",
                          "object": {
                            "sha": "stale-different-sha"
                          }
                        }
                        """);
            } else {
                sendJsonResponse(exchange, 404, "{\"message\":\"Not Found\"}");
            }
        });

        GithubMutationResponse response = githubMutationService.enqueueExecution(
                data.adminUser.getId(),
                data.organization.getId(),
                data.approval.getId()
        );

        githubMutationExecutor.execute(response.id());

        GithubMutation updated = githubMutationRepository.findById(response.id()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(GithubMutationStatus.FAILED);
        assertThat(updated.getFailureCode()).isEqualTo("BASE_REVISION_STALE");
        assertThat(updated.getFailureMessage()).contains("Remote base revision");

        List<TaskEvent> events = taskEventRepository.findAllByTaskIdAndOrganizationId(
                data.task.getId(), data.organization.getId(), PageRequest.of(0, 50)).getContent();
        List<TaskEventType> eventTypes = events.stream().map(TaskEvent::getEventType).toList();
        assertThat(eventTypes).contains(TaskEventType.GITHUB_MUTATION_FAILED);
    }

    @Test
    @DisplayName("Reconciliation prevents duplicate PR creation if PR already exists on GitHub")
    void testReconcilesExistingPullRequest() {
        TestData data = createTestData("commit-base-sha-1");

        fakeGithubServer.createContext("/repos/acme/my-repo", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/pulls")) {
                sendJsonResponse(exchange, 200, """
                        [
                          {
                            "number": 88,
                            "html_url": "https://github.com/acme/my-repo/pull/88",
                            "state": "open"
                          }
                        ]
                        """);
            } else {
                sendJsonResponse(exchange, 404, "{\"message\":\"Not Found\"}");
            }
        });

        GithubMutationResponse response = githubMutationService.enqueueExecution(
                data.adminUser.getId(),
                data.organization.getId(),
                data.approval.getId()
        );

        githubMutationExecutor.execute(response.id());

        GithubMutation updated = githubMutationRepository.findById(response.id()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(GithubMutationStatus.SUCCEEDED);
        assertThat(updated.getPullRequestNumber()).isEqualTo(88L);
        assertThat(updated.getPullRequestUrl()).isEqualTo("https://github.com/acme/my-repo/pull/88");
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }

    private record TestData(
            Organization organization,
            Repository repository,
            Task task,
            User adminUser,
            ProposedChange proposedChange,
            SandboxExecution sandboxExecution,
            Approval approval
    ) {}

    private TestData createTestData(String baseRevision) {
        Instant now = Instant.now();

        User adminUser = userRepository.save(new User(
                "admin-" + UUID.randomUUID() + "@example.com",
                "Admin User",
                "ext-" + UUID.randomUUID(),
                "hash",
                true,
                now,
                now
        ));

        Organization org = organizationRepository.save(new Organization(
                "Org Name",
                "org-" + UUID.randomUUID(),
                now,
                now
        ));

        organizationMemberRepository.save(new OrganizationMember(
                org,
                adminUser,
                OrganizationRole.ADMIN,
                now
        ));

        Repository repo = new Repository(
                org,
                RepositoryProvider.GITHUB,
                "repo-" + UUID.randomUUID(),
                "acme/my-repo",
                "main",
                true,
                now,
                now
        );
        repo.associateGithubInstallation(12345L);
        repo = repositoryRepository.save(repo);

        Task task = taskRepository.save(new Task(
                org,
                repo,
                adminUser,
                TaskSourceType.MANUAL,
                null,
                "Test task",
                "Description",
                TaskStatus.RUNNING,
                "main",
                "running",
                null,
                now,
                now,
                null,
                null
        ));

        ProposedChange pc = new ProposedChange(
                task,
                org,
                repo,
                ProposedChangeStatus.VALIDATED,
                "Add File",
                "Adding Hello.java",
                baseRevision,
                """
                diff --git a/Hello.java b/Hello.java
                new file mode 100644
                --- /dev/null
                +++ b/Hello.java
                @@ -0,0 +1,1 @@
                +public class Hello {}
                """,
                "{\"status\":\"VALIDATED\"}",
                now
        );
        pc.addFile(new ProposedChangeFile(
                "Hello.java",
                ProposedChangeOperation.ADD,
                1,
                0,
                """
                diff --git a/Hello.java b/Hello.java
                new file mode 100644
                --- /dev/null
                +++ b/Hello.java
                @@ -0,0 +1,1 @@
                +public class Hello {}
                """
        ));
        pc = proposedChangeRepository.save(pc);

        SandboxExecution execution = sandboxExecutionRepository.save(new SandboxExecution(
                pc,
                task,
                org,
                repo,
                SandboxExecutionProfile.JAVA_MAVEN_TEST,
                now
        ));
        execution.markRunning(now.plusSeconds(1));
        execution.complete(SandboxExecutionStatus.SUCCEEDED, 0, "BUILD SUCCESS", "", false, null, null, now.plusSeconds(5));
        execution = sandboxExecutionRepository.save(execution);

        Approval approval = approvalRepository.save(new Approval(
                pc,
                task,
                org,
                repo,
                pc.getVersion(),
                execution,
                adminUser,
                now
        ));
        approval.approve(adminUser, "Approved for merge", now.plusSeconds(10));
        approval = approvalRepository.save(approval);

        return new TestData(org, repo, task, adminUser, pc, execution, approval);
    }
}
