package com.devflow.api.persistence;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.async.config.AsyncJobConfiguration;
import com.devflow.api.async.persistence.AsyncJobStore;
import com.devflow.api.github.webhook.domain.GithubWebhookEvent;
import com.devflow.api.github.webhook.persistence.GithubWebhookEventRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.organization.OrganizationRepository;
import com.devflow.api.persistence.repository.RepositoryRepository;
import com.devflow.api.persistence.task.TaskRepository;
import com.devflow.api.persistence.task.TaskEventRepository;
import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskSourceType;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.task.domain.TaskEventType;
import com.devflow.api.task.domain.TaskActor;
import com.devflow.api.task.api.CreateTaskRequest;
import com.devflow.api.task.service.TaskService;
import com.devflow.api.task.service.TaskStateService;
import com.devflow.api.agent.exception.AgentExecutionException;
import com.devflow.api.agent.service.AgentTaskContextLoader;
import com.devflow.api.agent.domain.AgentExecutionContext;
import com.devflow.api.proposedchange.api.ProposedChangeCommand;
import com.devflow.api.proposedchange.api.ProposedChangeFileCommand;
import com.devflow.api.proposedchange.config.ProposedChangeConfiguration;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeOperation;
import com.devflow.api.proposedchange.exception.ProposedChangeException;
import com.devflow.api.persistence.proposedchange.ProposedChangeRepository;
import com.devflow.api.proposedchange.service.ProposedChangeService;
import com.devflow.api.proposedchange.validation.ProposedChangePatchValidator;
import com.devflow.api.proposedchange.validation.ProposedChangePathValidator;
import com.devflow.api.persistence.sandbox.SandboxExecutionRepository;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.approval.domain.Approval;
import com.devflow.api.approval.domain.ApprovalStatus;
import com.devflow.api.persistence.approval.ApprovalRepository;
import com.devflow.api.github.mutation.domain.GithubMutation;
import com.devflow.api.github.mutation.domain.GithubMutationStatus;
import com.devflow.api.persistence.githubmutation.GithubMutationRepository;
import com.devflow.api.web.ApiException;
import com.devflow.api.user.domain.User;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AsyncJobConfiguration.class, AsyncJobStore.class, TaskStateService.class, TaskService.class,
        AgentTaskContextLoader.class, ProposedChangeConfiguration.class, ProposedChangeService.class,
        ProposedChangePatchValidator.class, ProposedChangePathValidator.class,
        DomainPersistenceIntegrationTest.AsyncTestConfiguration.class})
class DomainPersistenceIntegrationTest {

    @TestConfiguration
    static class AsyncTestConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
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
    private GithubWebhookEventRepository githubWebhookEventRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AgentTaskContextLoader agentTaskContextLoader;

    @Autowired
    private ProposedChangeService proposedChangeService;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AsyncJobStore asyncJobStore;

    @Autowired
    private TaskStateService taskStateService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsTaskCreatedEventAndEnforcesEventIdempotency() {
        PersistedGraph graph = persistGraph("events@example.com", "events-auth", "events", "events-repo");
        Instant occurredAt = Instant.now();
        TaskEvent event = taskEventRepository.saveAndFlush(new TaskEvent(
                graph.task(), graph.organization(), TaskEventType.TASK_CREATED, null,
                TaskStatus.QUEUED, TaskActor.user(graph.user().getId()), occurredAt, null, "create-1"));

        assertThat(taskEventRepository.findById(event.getId())).isPresent();
        assertThat(taskEventRepository.findAllByTaskIdAndOrganizationId(
                graph.task().getId(), graph.organization().getId(), PageRequest.of(0, 10)))
                .hasSize(1);

        TaskEvent duplicate = new TaskEvent(
                graph.task(), graph.organization(), TaskEventType.TASK_CREATED, null,
                TaskStatus.QUEUED, TaskActor.user(graph.user().getId()), occurredAt, null, "create-1");
        assertThatThrownBy(() -> taskEventRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidEventActorCombinationAtDatabaseBoundary() {
        PersistedGraph graph = persistGraph("event-constraints@example.com", "event-constraints-auth",
                "event-constraints", "event-constraints-repo");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO task_events
                    (id, task_id, organization_id, event_type, new_state, actor_source, actor_user_id)
                VALUES (?, ?, ?, 'TASK_STARTED', 'RUNNING', 'SYSTEM', ?)
                """, UUID.randomUUID(), graph.task().getId(), graph.organization().getId(),
                graph.user().getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackTaskTransitionWhenEventInsertFails() {
        PersistedGraph graph = persistGraph("rollback@example.com", "rollback-auth",
                "rollback", "rollback-repo");
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION reject_started_task_event()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'TASK_STARTED' THEN
                        RAISE EXCEPTION 'test event failure';
                    END IF;
                    RETURN NEW;
                END $$;
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_started_task_event_trigger
                BEFORE INSERT ON task_events
                FOR EACH ROW EXECUTE FUNCTION reject_started_task_event()
                """);
        try {
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                    .execute(status -> {
                        taskStateService.startTask(graph.organization().getId(), graph.task().getId(),
                                TaskActor.worker(), "rollback-1");
                        return null;
                    })).isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER reject_started_task_event_trigger ON task_events");
            jdbcTemplate.execute("DROP FUNCTION reject_started_task_event()");
        }

        entityManager.clear();
        assertThat(taskRepository.findById(graph.task().getId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.QUEUED);
        assertThat(taskEventRepository.findAllByTaskIdAndOrganizationId(
                graph.task().getId(), graph.organization().getId(), PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackTaskCreationWhenCreatedEventInsertFails() {
        PersistedGraph graph = persistGraph("creation-rollback@example.com", "creation-rollback-auth",
                "creation-rollback", "creation-rollback-repo");
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION reject_created_task_event()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'TASK_CREATED' THEN
                        RAISE EXCEPTION 'test task creation event failure';
                    END IF;
                    RETURN NEW;
                END $$;
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_created_task_event_trigger
                BEFORE INSERT ON task_events
                FOR EACH ROW EXECUTE FUNCTION reject_created_task_event()
                """);
        try {
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                    .execute(status -> {
                        taskService.create(graph.user().getId(), graph.organization().getId(),
                                new CreateTaskRequest(graph.repository().getId(), "Rollback task", null));
                        return null;
                    })).isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER reject_created_task_event_trigger ON task_events");
            jdbcTemplate.execute("DROP FUNCTION reject_created_task_event()");
        }

        entityManager.clear();
        assertThat(taskRepository.findAllByOrganizationId(
                graph.organization().getId(), PageRequest.of(0, 20)).getContent())
                .noneMatch(task -> "Rollback task".equals(task.getTitle()));
        assertThat(taskEventRepository.findAllByTaskIdAndOrganizationId(
                graph.task().getId(), graph.organization().getId(), PageRequest.of(0, 20)))
                .isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void optimisticLockAllowsOnlyOneConcurrentTransition() throws Exception {
        PersistedGraph graph = persistGraph("concurrent@example.com", "concurrent-auth",
                "concurrent", "concurrent-repo");
        CountDownLatch loaded = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<TaskStatus> committed = new CopyOnWriteArrayList<>();
        Thread first = new Thread(() -> concurrentUpdate(
                graph.task().getId(), TaskStatus.RUNNING, loaded, release, failures, committed));
        Thread second = new Thread(() -> concurrentUpdate(
                graph.task().getId(), TaskStatus.CANCELLED, loaded, release, failures, committed));
        first.start();
        second.start();
        assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        first.join(10_000);
        second.join(10_000);

        assertThat(committed).hasSize(1);
        assertThat(failures).hasSize(1);
        entityManager.clear();
        assertThat(taskRepository.findById(graph.task().getId()).orElseThrow().getStatus())
                .isIn(TaskStatus.RUNNING, TaskStatus.CANCELLED);
        assertThat(taskEventRepository.findAllByTaskIdAndOrganizationId(
                graph.task().getId(), graph.organization().getId(), PageRequest.of(0, 10)))
                .hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void idempotentWorkerTransitionPersistsOneEvent() {
        PersistedGraph graph = persistGraph("idempotent@example.com", "idempotent-auth",
                "idempotent", "idempotent-repo");
        taskStateService.startTask(graph.organization().getId(), graph.task().getId(),
                TaskActor.worker(), "worker-transition-1");
        taskStateService.startTask(graph.organization().getId(), graph.task().getId(),
                TaskActor.worker(), "worker-transition-1");

        entityManager.clear();
        assertThat(taskEventRepository.findAllByTaskIdAndOrganizationId(
                graph.task().getId(), graph.organization().getId(), PageRequest.of(0, 10)))
                .hasSize(1)
                .first()
                .extracting(TaskEvent::getEventType)
                .isEqualTo(TaskEventType.TASK_STARTED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSameIdempotencyKeyPersistsOneTransitionAndEvent() throws Exception {
        PersistedGraph graph = persistGraph("concurrent-idempotency@example.com",
                "concurrent-idempotency-auth", "concurrent-idempotency", "concurrent-idempotency-repo");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CopyOnWriteArrayList<TaskStatus> successes = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();
        Runnable operation = () -> {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    taskRepository.findById(graph.task().getId()).orElseThrow();
                    ready.countDown();
                    await(release);
                    Task result = taskStateService.startTask(
                            graph.organization().getId(), graph.task().getId(),
                            TaskActor.worker(), "same-concurrent-key");
                    successes.add(result.getStatus());
                });
            } catch (Throwable failure) {
                failures.add(failure);
            }
        };
        Thread first = new Thread(operation);
        Thread second = new Thread(operation);
        first.start();
        second.start();
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        first.join(10_000);
        second.join(10_000);

        assertThat(successes).hasSize(1).containsExactly(TaskStatus.RUNNING);
        assertThat(failures).hasSize(1);
        entityManager.clear();
        assertThat(taskRepository.findById(graph.task().getId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.RUNNING);
        assertThat(taskEventRepository.findAllByTaskIdAndOrganizationId(
                graph.task().getId(), graph.organization().getId(), PageRequest.of(0, 20)))
                .hasSize(1)
                .first()
                .extracting(TaskEvent::getEventType)
                .isEqualTo(TaskEventType.TASK_STARTED);
    }

    private void concurrentUpdate(
            UUID taskId, TaskStatus next, CountDownLatch loaded, CountDownLatch release,
            CopyOnWriteArrayList<Throwable> failures, CopyOnWriteArrayList<TaskStatus> committed) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Task task = taskRepository.findById(taskId).orElseThrow();
                loaded.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrency barrier timed out");
                    }

                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Concurrency test interrupted", exception);
                }
                task.transitionTo(next, Instant.now());
                taskRepository.saveAndFlush(task);
                taskEventRepository.saveAndFlush(new TaskEvent(
                        task,
                        task.getOrganization(),
                        next == TaskStatus.RUNNING
                                ? TaskEventType.TASK_STARTED : TaskEventType.TASK_CANCELLED,
                        TaskStatus.QUEUED,
                        next,
                        TaskActor.worker(),
                        Instant.now(),
                        null,
                        null));
                committed.add(next);
            });
        } catch (Throwable failure) {
            failures.add(failure);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrency barrier timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency test interrupted", exception);
        }
    }

    @Test
    void persistsDomainGraphAndSupportsRepositoryQueries() {
        PersistedGraph graph = persistGraph("alice@example.com", "alice-auth", "devflow", "repo-1");

        Task foundTask = taskRepository.findById(graph.task().getId()).orElseThrow();
        assertThat(foundTask.getTitle()).isEqualTo("Wire persistence");
        assertThat(foundTask.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(foundTask.getOrganization().getSlug()).isEqualTo("devflow");
        assertThat(foundTask.getRepository().getFullName()).isEqualTo("devflow/api");
        assertThat(foundTask.getCreatedBy().getEmail()).isEqualTo("alice@example.com");

        assertThat(organizationMemberRepository.existsByIdUserIdAndIdOrganizationId(
                graph.user().getId(), graph.organization().getId())).isTrue();
        assertThat(organizationMemberRepository.findByIdOrganizationIdAndIdUserId(
                graph.organization().getId(), graph.user().getId())).hasValueSatisfying(member ->
                assertThat(member.getRole()).isEqualTo(OrganizationRole.OWNER));

        assertThat(repositoryRepository.findByProviderAndExternalId(RepositoryProvider.GITHUB, "repo-1"))
                .hasValueSatisfying(repository -> assertThat(repository.getFullName()).isEqualTo("devflow/api"));

        Page<Task> tasksByOrganization = taskRepository.findAllByOrganizationId(
                graph.organization().getId(), PageRequest.of(0, 10));
        assertThat(tasksByOrganization.getTotalElements()).isEqualTo(1);
        assertThat(tasksByOrganization.getContent()).extracting(Task::getTitle).containsExactly("Wire persistence");
    }

    @Test
    void rejectsDuplicateUserEmail() {
        Instant now = Instant.now();
        userRepository.saveAndFlush(new User("duplicate@example.com", "First", "first-auth", true, now, now));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("duplicate@example.com", "Second", "second-auth", true, now, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsWebhookAndEnforcesUniqueDeliveryId() {
        PersistedGraph graph = persistGraph("webhook@example.com", "webhook-auth", "webhooks", "webhook-repo");
        GithubWebhookEvent event = new GithubWebhookEvent(
                "delivery-unique-1", "issues", true, 42L, "webhook-repo",
                "webhooks/api", graph.organization(), graph.repository(),
                JsonNodeFactory.instance.objectNode(), Instant.now());

        githubWebhookEventRepository.saveAndFlush(event);
        assertThat(githubWebhookEventRepository.findByDeliveryId("delivery-unique-1")).hasValueSatisfying(found -> {
            assertThat(found.isSupported()).isTrue();
            assertThat(found.getRepository().getId()).isEqualTo(graph.repository().getId());
        });

        GithubWebhookEvent duplicate = new GithubWebhookEvent(
                "delivery-unique-1", "issues", true, 42L, "webhook-repo",
                "webhooks/api", graph.organization(), graph.repository(),
                JsonNodeFactory.instance.objectNode(), Instant.now());
        assertThatThrownBy(() -> githubWebhookEventRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsDurableJobAndOutboxRowsTogether() {
        PersistedGraph graph = persistGraph("outbox@example.com", "outbox-auth", "outbox", "outbox-repo");
        GithubWebhookEvent event = new GithubWebhookEvent(
                "delivery-outbox-1", "issues", true, 42L, "outbox-repo",
                "outbox/api", graph.organization(), graph.repository(),
                JsonNodeFactory.instance.objectNode(), Instant.now());

        githubWebhookEventRepository.saveAndFlush(event);
        java.util.UUID jobId = asyncJobStore.enqueueWebhook(event.getId());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM async_jobs WHERE job_type = 'WEBHOOK_PROCESSING'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM async_job_outbox WHERE job_type = 'WEBHOOK_PROCESSING'", Integer.class))
                .isEqualTo(1);

        jdbcTemplate.update("UPDATE async_jobs SET status = 'PROCESSING', attempts = 1 WHERE id = ?", jobId);
        jdbcTemplate.update("UPDATE async_job_outbox SET status = 'PUBLISHED' WHERE job_id = ?", jobId);
        asyncJobStore.markFailedOrRetry(jobId, "transient", 1, 5, 10);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload->>'attempt' FROM async_job_outbox WHERE job_id = ?", String.class, jobId))
                .isEqualTo("2");
    }

    @Test
    void pagesTasksByRepository() {
        PersistedGraph graph = persistGraph("bob@example.com", "bob-auth", "platform", "repo-2");

        Page<Task> page = taskRepository.findAllByRepositoryId(graph.repository().getId(), PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(Task::getSourceType).containsExactly(TaskSourceType.MANUAL);
    }

    @Test
    void rejectsInvalidDatabaseEnumValues() {
        PersistedGraph graph = persistGraph("carol@example.com", "carol-auth", "infra", "repo-3");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO tasks (
                    organization_id, repository_id, created_by_user_id, source_type, title, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                graph.organization().getId(),
                graph.repository().getId(),
                graph.user().getId(),
                "EMAIL",
                "Invalid source",
                "NOT_REAL",
                Instant.now(),
                Instant.now()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void scopesRepositoryExternalIdsToOrganization() {
        PersistedGraph first = persistGraph("dave@example.com", "dave-auth", "first", "shared-repo");
        PersistedGraph second = persistGraph("erin@example.com", "erin-auth", "second", "shared-repo");

        assertThat(first.repository().getId()).isNotEqualTo(second.repository().getId());
    }

    @Test
    void databaseRejectsTaskReferencingRepositoryFromAnotherOrganization() {
        PersistedGraph first = persistGraph("frank@example.com", "frank-auth", "first-task", "repo-a");
        PersistedGraph second = persistGraph("gina@example.com", "gina-auth", "second-task", "repo-b");
        Instant now = Instant.now();

        assertThatThrownBy(() -> taskRepository.saveAndFlush(new Task(
                second.organization(),
                first.repository(),
                second.user(),
                TaskSourceType.MANUAL,
                null,
                "Cross tenant task",
                null,
                TaskStatus.QUEUED,
                null,
                null,
                null,
                now,
                now,
                null,
                null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void agentContextLoaderRejectsCrossOrganizationTaskAccess() {
        PersistedGraph first = persistGraph("agent-a@example.com", "agent-a-auth", "agent-a", "agent-repo-a");
        PersistedGraph second = persistGraph("agent-b@example.com", "agent-b-auth", "agent-b", "agent-repo-b");

        assertThatThrownBy(() -> agentTaskContextLoader.load(
                first.user().getId(), first.organization().getId(), second.task().getId()))
                .isInstanceOf(AgentExecutionException.class);
        assertThat(agentTaskContextLoader.load(
                first.user().getId(), first.organization().getId(), first.task().getId()).repositoryId())
                .isEqualTo(first.repository().getId());
    }

    @Test
    void persistsValidatedProposedChangeWithTenantContext() {
        PersistedGraph graph = persistGraph("proposal@example.com", "proposal-auth", "proposal-org", "proposal-repo");
        String patch = """
                diff --git a/README.md b/README.md
                --- a/README.md
                +++ b/README.md
                @@ -1,1 +1,2 @@
                 old
                +new
                """;
        var proposal = proposedChangeService.create(
                new AgentExecutionContext(graph.user().getId(), graph.organization().getId(),
                        graph.task().getId(), graph.repository().getId(), "objective", "repo"),
                new ProposedChangeCommand("Update README", "Document the change", "main", patch,
                        java.util.List.of(new ProposedChangeFileCommand(
                                "README.md", ProposedChangeOperation.MODIFY, 1, 0, patch.trim()))));

        assertThat(proposal.getId()).isNotNull();
        assertThat(proposal.getStatus().name()).isEqualTo("VALIDATED");
        assertThat(proposal.getOrganization().getId()).isEqualTo(graph.organization().getId());
        assertThat(proposal.getTask().getId()).isEqualTo(graph.task().getId());
    }

    @Test
    void rejectsProposalTaskRepositoryMismatch() {
        PersistedGraph first = persistGraph("proposal-a@example.com", "proposal-a-auth", "proposal-a", "proposal-repo-a");
        PersistedGraph second = persistGraph("proposal-b@example.com", "proposal-b-auth", "proposal-b", "proposal-repo-b");
        ProposedChangeCommand command = validProposalCommand();

        assertThatThrownBy(() -> proposedChangeService.create(
                new AgentExecutionContext(first.user().getId(), first.organization().getId(),
                        first.task().getId(), second.repository().getId(), "objective", "repo"), command))
                .isInstanceOf(ProposedChangeException.class);
    }

    @Test
    void rejectsTaskFromOrgAWithRepositoryFromOrgB() {
        PersistedGraph first = persistGraph("tenant-a@example.com", "tenant-a-auth", "tenant-a", "tenant-repo-a");
        PersistedGraph second = persistGraph("tenant-b@example.com", "tenant-b-auth", "tenant-b", "tenant-repo-b");

        assertThatThrownBy(() -> proposedChangeService.create(
                new AgentExecutionContext(first.user().getId(), first.organization().getId(),
                        first.task().getId(), second.repository().getId(), "objective", "repo"),
                validProposalCommand()))
                .isInstanceOf(ProposedChangeException.class);
    }

    @Test
    void rejectsCrossOrganizationProposalLookup() {
        PersistedGraph first = persistGraph("lookup-a@example.com", "lookup-a-auth", "lookup-a", "lookup-repo-a");
        PersistedGraph second = persistGraph("lookup-b@example.com", "lookup-b-auth", "lookup-b", "lookup-repo-b");

        var proposal = proposedChangeService.create(
                new AgentExecutionContext(first.user().getId(), first.organization().getId(),
                        first.task().getId(), first.repository().getId(), "objective", "repo"),
                validProposalCommand());

        assertThat(proposedChangeService.getById(proposal.getId(), first.organization().getId())).isNotNull();

        assertThatThrownBy(() -> proposedChangeService.getById(proposal.getId(), second.organization().getId()))
                .isInstanceOf(ProposedChangeException.class);

        assertThat(proposedChangeRepository.findByIdAndOrganizationId(proposal.getId(), second.organization().getId()))
                .isEmpty();
    }

    @Test
    void rejectsInactiveRepository() {
        PersistedGraph graph = persistGraph("inactive-repo@example.com", "inactive-auth", "inactive-org", "inactive-repo-1");
        Instant now = Instant.now();
        Repository inactiveRepo = repositoryRepository.saveAndFlush(new Repository(
                graph.organization(),
                RepositoryProvider.GITHUB,
                "inactive-repo-2",
                "devflow/inactive-repo",
                "main",
                false,
                now,
                now));
        Task taskForInactiveRepo = taskRepository.saveAndFlush(new Task(
                graph.organization(),
                inactiveRepo,
                graph.user(),
                TaskSourceType.MANUAL,
                null,
                "Inactive repo task",
                "Task description",
                TaskStatus.QUEUED,
                "task/inactive",
                "queued",
                null,
                now,
                now,
                null,
                null));

        ProposedChangeCommand command = validProposalCommand();
        assertThatThrownBy(() -> proposedChangeService.create(
                new AgentExecutionContext(graph.user().getId(), graph.organization().getId(),
                        taskForInactiveRepo.getId(), inactiveRepo.getId(), "objective", "repo"), command))
                .isInstanceOf(ProposedChangeException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void rejectsMissingTaskSafely() {
        PersistedGraph graph = persistGraph("missing-task@example.com", "missing-task-auth", "missing-task", "missing-task-repo");
        UUID nonExistentTaskId = UUID.randomUUID();

        assertThatThrownBy(() -> proposedChangeService.create(
                new AgentExecutionContext(graph.user().getId(), graph.organization().getId(),
                        nonExistentTaskId, graph.repository().getId(), "objective", "repo"),
                validProposalCommand()))
                .isInstanceOf(ProposedChangeException.class);
    }

    @Test
    void rejectsMissingRepositoryOrContextSafely() {
        PersistedGraph graph = persistGraph("missing-ctx@example.com", "missing-ctx-auth", "missing-ctx", "missing-ctx-repo");
        ProposedChangeCommand command = validProposalCommand();

        assertThatThrownBy(() -> proposedChangeService.create(null, command))
                .isInstanceOf(ProposedChangeException.class);

        assertThatThrownBy(() -> proposedChangeService.create(
                new AgentExecutionContext(graph.user().getId(), graph.organization().getId(),
                        graph.task().getId(), null, "objective", "repo"), command))
                .isInstanceOf(ProposedChangeException.class);

        assertThatThrownBy(() -> proposedChangeService.create(
                new AgentExecutionContext(graph.user().getId(), null,
                        graph.task().getId(), graph.repository().getId(), "objective", "repo"), command))
                .isInstanceOf(ProposedChangeException.class);
    }

    @Test
    void databaseCompositeTenantConstraintsPreventInvalidAssociations() {
        PersistedGraph first = persistGraph("db-tenant-a@example.com", "db-tenant-a-auth", "db-tenant-a", "db-repo-a");
        PersistedGraph second = persistGraph("db-tenant-b@example.com", "db-tenant-b-auth", "db-tenant-b", "db-repo-b");

        // Attempting to insert a proposed_change with organization_id = first, but repository_id = second
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO proposed_changes
                    (id, task_id, organization_id, repository_id, status, title, description, base_revision, patch, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'VALIDATED', 'Invalid proposal', 'Description', 'main', 'patch', 0, now(), now())
                """,
                UUID.randomUUID(), first.task().getId(), first.organization().getId(), second.repository().getId()))
                .isInstanceOf(DataAccessException.class);

        // Attempting to insert a proposed_change with organization_id = second, but task_id = first
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO proposed_changes
                    (id, task_id, organization_id, repository_id, status, title, description, base_revision, patch, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'VALIDATED', 'Invalid proposal', 'Description', 'main', 'patch', 0, now(), now())
                """,
                UUID.randomUUID(), first.task().getId(), second.organization().getId(), second.repository().getId()))
                .isInstanceOf(DataAccessException.class);
    }

    private ProposedChangeCommand validProposalCommand() {
        String patch = """
                diff --git a/README.md b/README.md
                --- a/README.md
                +++ b/README.md
                @@ -1,1 +1,2 @@
                 old
                +new
                """;
        return new ProposedChangeCommand("Update README", "Document the change", "main", patch,
                java.util.List.of(new ProposedChangeFileCommand(
                        "README.md", ProposedChangeOperation.MODIFY, 1, 0, patch.trim())));
    }

    private PersistedGraph persistGraph(String email, String externalAuthId, String slug, String repositoryExternalId) {
        Instant now = Instant.now();
        User user = userRepository.saveAndFlush(new User(email, "Test User", externalAuthId, true, now, now));
        Organization organization = organizationRepository.saveAndFlush(new Organization("DevFlow", slug, now, now));
        OrganizationMember member = organizationMemberRepository.saveAndFlush(
                new OrganizationMember(organization, user, OrganizationRole.OWNER, now));
        Repository repository = repositoryRepository.saveAndFlush(new Repository(
                organization,
                RepositoryProvider.GITHUB,
                repositoryExternalId,
                "devflow/api",
                "main",
                true,
                now,
                now));
        Task task = taskRepository.saveAndFlush(new Task(
                organization,
                repository,
                user,
                TaskSourceType.MANUAL,
                null,
                "Wire persistence",
                "Map domain objects to the existing schema",
                TaskStatus.QUEUED,
                "task/wire-persistence",
                "queued",
                null,
                now,
                now,
                null,
                null));
        return new PersistedGraph(user, organization, member, repository, task);
    }

    @Test
    void persistsSandboxExecutionAndValidatesFlywayV10() {

        PersistedGraph graph = persistGraph("sandbox@example.com", "sandbox-auth", "sandbox-org", "sandbox-repo");
        Instant now = Instant.now();

        ProposedChange change = proposedChangeRepository.saveAndFlush(new ProposedChange(
                graph.task(), graph.organization(), graph.repository(),
                com.devflow.api.proposedchange.domain.ProposedChangeStatus.VALIDATED,
                "Add test", "Description", "main",
                "diff --git a/A.java b/A.java\n--- /dev/null\n+++ b/A.java\n@@ -0,0 +1,1 @@\n+class A {}",
                "{\"status\":\"VALIDATED\"}", now
        ));

        SandboxExecution execution = new SandboxExecution(
                change, graph.task(), graph.organization(), graph.repository(),
                SandboxExecutionProfile.JAVA_MAVEN_TEST, now
        );
        execution = sandboxExecutionRepository.saveAndFlush(execution);
        UUID executionId = execution.getId();
        long initialVersion = execution.getVersion();

        // Verify eager fetch with details
        SandboxExecution loaded = sandboxExecutionRepository.findByIdWithDetails(executionId).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(SandboxExecutionStatus.QUEUED);
        assertThat(loaded.getProfile()).isEqualTo(SandboxExecutionProfile.JAVA_MAVEN_TEST);
        assertThat(loaded.getTask().getId()).isEqualTo(graph.task().getId());
        assertThat(loaded.getOrganization().getId()).isEqualTo(graph.organization().getId());
        assertThat(loaded.getRepository().getId()).isEqualTo(graph.repository().getId());
        assertThat(loaded.getProposedChange().getId()).isEqualTo(change.getId());

        // Transition to RUNNING
        assertThat(loaded.markRunning(now)).isTrue();
        loaded = sandboxExecutionRepository.saveAndFlush(loaded);
        assertThat(loaded.getVersion()).isGreaterThan(initialVersion);
        assertThat(loaded.getStatus()).isEqualTo(SandboxExecutionStatus.RUNNING);

        // Transition to SUCCEEDED
        assertThat(loaded.complete(SandboxExecutionStatus.SUCCEEDED, 0, "BUILD SUCCESS", "", false, null, null, now.plusSeconds(3))).isTrue();
        loaded = sandboxExecutionRepository.saveAndFlush(loaded);
        assertThat(loaded.getStatus()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(loaded.getExitCode()).isEqualTo(0);
        assertThat(loaded.getStdout()).isEqualTo("BUILD SUCCESS");
        assertThat(loaded.getDuration()).isGreaterThanOrEqualTo(0L);

        // Verify findByProposedChangeId
        assertThat(sandboxExecutionRepository.findByProposedChangeIdOrderByCreatedAtDesc(change.getId())).hasSize(1);
    }

    @Test
    void enforcesSandboxExecutionTenantIsolationAndConstraints() {
        PersistedGraph graph = persistGraph("sandbox-iso@example.com", "sandbox-iso-auth", "sandbox-iso-org", "sandbox-iso-repo");
        Instant now = Instant.now();

        ProposedChange change = proposedChangeRepository.saveAndFlush(new ProposedChange(
                graph.task(), graph.organization(), graph.repository(),
                com.devflow.api.proposedchange.domain.ProposedChangeStatus.VALIDATED,
                "Tenant change", "Description", "main",
                "diff --git a/B.java b/B.java\n--- /dev/null\n+++ b/B.java\n@@ -0,0 +1,1 @@\n+class B {}",
                "{\"status\":\"VALIDATED\"}", now
        ));

        SandboxExecution execution = sandboxExecutionRepository.saveAndFlush(new SandboxExecution(
                change, graph.task(), graph.organization(), graph.repository(),
                SandboxExecutionProfile.JAVA_MAVEN_TEST, now
        ));

        // Cross-organization query returns empty
        UUID foreignOrgId = UUID.randomUUID();
        assertThat(sandboxExecutionRepository.findByIdAndOrganizationId(execution.getId(), foreignOrgId)).isEmpty();

        // Matching organization query succeeds
        assertThat(sandboxExecutionRepository.findByIdAndOrganizationId(execution.getId(), graph.organization().getId())).isPresent();
    }

    @Test
    void persistsAndTransitionsApprovalAggregate() {
        PersistedGraph graph = persistGraph("approval-test@example.com", "approval-auth", "approval-org", "approval-repo");
        Instant now = Instant.now();

        ProposedChange change = proposedChangeRepository.saveAndFlush(new ProposedChange(
                graph.task(), graph.organization(), graph.repository(),
                com.devflow.api.proposedchange.domain.ProposedChangeStatus.VALIDATED,
                "Approval test change", "Description", "main",
                "diff --git a/A.java b/A.java\n--- /dev/null\n+++ b/A.java\n@@ -0,0 +1,1 @@\n+class A {}",
                "{\"status\":\"VALIDATED\"}", now
        ));

        SandboxExecution execution = new SandboxExecution(
                change, graph.task(), graph.organization(), graph.repository(),
                SandboxExecutionProfile.JAVA_MAVEN_TEST, now
        );
        execution.complete(SandboxExecutionStatus.SUCCEEDED, 0, "BUILD SUCCESS", "", false, null, null, now.plusSeconds(2));
        execution = sandboxExecutionRepository.saveAndFlush(execution);

        // 1. Create Approval
        Approval approval = new Approval(
                change, graph.task(), graph.organization(), graph.repository(),
                change.getVersion(), execution, graph.user(), now
        );
        approval = approvalRepository.saveAndFlush(approval);
        UUID approvalId = approval.getId();
        long initialVersion = approval.getVersion();

        // 2. Load with details
        Approval loaded = approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, graph.organization().getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(loaded.getProposedChange().getId()).isEqualTo(change.getId());
        assertThat(loaded.getTask().getId()).isEqualTo(graph.task().getId());
        assertThat(loaded.getOrganization().getId()).isEqualTo(graph.organization().getId());
        assertThat(loaded.getRepository().getId()).isEqualTo(graph.repository().getId());
        assertThat(loaded.getSandboxExecution().getId()).isEqualTo(execution.getId());
        assertThat(loaded.getRequestedBy().getId()).isEqualTo(graph.user().getId());
        assertThat(loaded.getDecidedBy()).isNull();

        // 3. Transition to APPROVED
        assertThat(loaded.approve(graph.user(), "Approved for deploy", now.plusSeconds(5))).isTrue();
        loaded = approvalRepository.saveAndFlush(loaded);

        assertThat(loaded.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(loaded.getDecidedBy().getId()).isEqualTo(graph.user().getId());
        assertThat(loaded.getDecisionReason()).isEqualTo("Approved for deploy");
        assertThat(loaded.getVersion()).isGreaterThan(initialVersion);
    }

    @Test
    void enforcesApprovalTenantIsolationAndConstraints() {
        PersistedGraph graphA = persistGraph("appr-a@example.com", "appr-a-auth", "appr-a-org", "appr-a-repo");
        PersistedGraph graphB = persistGraph("appr-b@example.com", "appr-b-auth", "appr-b-org", "appr-b-repo");
        Instant now = Instant.now();

        ProposedChange changeA = proposedChangeRepository.saveAndFlush(new ProposedChange(
                graphA.task(), graphA.organization(), graphA.repository(),
                com.devflow.api.proposedchange.domain.ProposedChangeStatus.VALIDATED,
                "Change A", "Description A", "main",
                "diff --git a/A.java b/A.java\n--- /dev/null\n+++ b/A.java\n@@ -0,0 +1,1 @@\n+class A {}",
                "{\"status\":\"VALIDATED\"}", now
        ));

        SandboxExecution executionA = sandboxExecutionRepository.saveAndFlush(new SandboxExecution(
                changeA, graphA.task(), graphA.organization(), graphA.repository(),
                SandboxExecutionProfile.JAVA_MAVEN_TEST, now
        ));

        Approval approvalA = approvalRepository.saveAndFlush(new Approval(
                changeA, graphA.task(), graphA.organization(), graphA.repository(),
                changeA.getVersion(), executionA, graphA.user(), now
        ));

        // Cross-org lookup fails
        assertThat(approvalRepository.findByIdAndOrganizationId(approvalA.getId(), graphB.organization().getId())).isEmpty();
        assertThat(approvalRepository.findByIdAndOrganizationIdWithDetails(approvalA.getId(), graphB.organization().getId())).isEmpty();

        // Same-org lookup succeeds
        assertThat(approvalRepository.findByIdAndOrganizationId(approvalA.getId(), graphA.organization().getId())).isPresent();
    }

    @Test
    void enforcesUniqueActivePendingApprovalPerProposalVersion() {
        PersistedGraph graph = persistGraph("dup-appr@example.com", "dup-appr-auth", "dup-appr-org", "dup-appr-repo");
        Instant now = Instant.now();

        ProposedChange change = proposedChangeRepository.saveAndFlush(new ProposedChange(
                graph.task(), graph.organization(), graph.repository(),
                com.devflow.api.proposedchange.domain.ProposedChangeStatus.VALIDATED,
                "Dup test change", "Description", "main",
                "diff --git a/D.java b/D.java\n--- /dev/null\n+++ b/D.java\n@@ -0,0 +1,1 @@\n+class D {}",
                "{\"status\":\"VALIDATED\"}", now
        ));

        SandboxExecution execution = sandboxExecutionRepository.saveAndFlush(new SandboxExecution(
                change, graph.task(), graph.organization(), graph.repository(),
                SandboxExecutionProfile.JAVA_MAVEN_TEST, now
        ));

        // First PENDING approval
        approvalRepository.saveAndFlush(new Approval(
                change, graph.task(), graph.organization(), graph.repository(),
                change.getVersion(), execution, graph.user(), now
        ));

        // Second PENDING approval for same change and version must trigger unique constraint violation
        assertThatThrownBy(() -> approvalRepository.saveAndFlush(new Approval(
                change, graph.task(), graph.organization(), graph.repository(),
                change.getVersion(), execution, graph.user(), now
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsAndUpdatesGithubMutationWithOptimisticLocking() {
        PersistedGraph graph = persistGraph("mut-lock@example.com", "mut-lock-auth", "mut-lock-org", "mut-lock-repo");
        Instant now = Instant.now();

        ProposedChange change = proposedChangeRepository.saveAndFlush(new ProposedChange(
                graph.task(), graph.organization(), graph.repository(),
                com.devflow.api.proposedchange.domain.ProposedChangeStatus.VALIDATED,
                "Mutation test change", "Description", "main",
                "diff --git a/M.java b/M.java\n--- /dev/null\n+++ b/M.java\n@@ -0,0 +1,1 @@\n+class M {}",
                "{\"status\":\"VALIDATED\"}", now
        ));

        SandboxExecution execution = sandboxExecutionRepository.saveAndFlush(new SandboxExecution(
                change, graph.task(), graph.organization(), graph.repository(),
                SandboxExecutionProfile.JAVA_MAVEN_TEST, now
        ));

        Approval approval = approvalRepository.saveAndFlush(new Approval(
                change, graph.task(), graph.organization(), graph.repository(),
                change.getVersion(), execution, graph.user(), now
        ));
        approval.approve(graph.user(), "Approved", now.plusSeconds(1));
        approval = approvalRepository.saveAndFlush(approval);

        GithubMutation mutation = new GithubMutation(
                graph.organization(), graph.repository(), graph.task(),
                change, change.getVersion(), approval, execution,
                "base123sha", "devflow/task-1/change-1", now
        );
        mutation = githubMutationRepository.saveAndFlush(mutation);

        assertThat(mutation.getId()).isNotNull();
        assertThat(mutation.getStatus()).isEqualTo(GithubMutationStatus.QUEUED);
        long initialVersion = mutation.getVersion();

        // 1. QUEUED -> RUNNING
        mutation.markRunning(now.plusSeconds(2));
        mutation = githubMutationRepository.saveAndFlush(mutation);
        assertThat(mutation.getStatus()).isEqualTo(GithubMutationStatus.RUNNING);
        assertThat(mutation.getVersion()).isGreaterThan(initialVersion);

        // 2. RUNNING -> COMMITTED
        mutation.recordCommit("commit123sha", now.plusSeconds(3));
        mutation = githubMutationRepository.saveAndFlush(mutation);
        assertThat(mutation.getStatus()).isEqualTo(GithubMutationStatus.COMMITTED);
        assertThat(mutation.getCommitSha()).isEqualTo("commit123sha");

        // 3. COMMITTED -> BRANCH_CREATED
        mutation.recordBranch("devflow/task-1/change-1", now.plusSeconds(4));
        mutation = githubMutationRepository.saveAndFlush(mutation);
        assertThat(mutation.getStatus()).isEqualTo(GithubMutationStatus.BRANCH_CREATED);

        // 4. BRANCH_CREATED -> PR_CREATED
        mutation.recordPullRequest(42, "https://github.com/org/repo/pull/42", now.plusSeconds(5));
        mutation = githubMutationRepository.saveAndFlush(mutation);
        assertThat(mutation.getStatus()).isEqualTo(GithubMutationStatus.PR_CREATED);
        assertThat(mutation.getPullRequestNumber()).isEqualTo(42);

        // 5. PR_CREATED -> SUCCEEDED
        mutation.markSucceeded(now.plusSeconds(6));
        mutation = githubMutationRepository.saveAndFlush(mutation);
        assertThat(mutation.getStatus()).isEqualTo(GithubMutationStatus.SUCCEEDED);
    }

    @Test
    void enforcesGithubMutationTenantIsolationAndConstraints() {
        PersistedGraph graphA = persistGraph("mut-a@example.com", "mut-a-auth", "mut-a-org", "mut-a-repo");
        PersistedGraph graphB = persistGraph("mut-b@example.com", "mut-b-auth", "mut-b-org", "mut-b-repo");
        Instant now = Instant.now();

        ProposedChange changeA = proposedChangeRepository.saveAndFlush(new ProposedChange(
                graphA.task(), graphA.organization(), graphA.repository(),
                com.devflow.api.proposedchange.domain.ProposedChangeStatus.VALIDATED,
                "Change A", "Description A", "main",
                "diff --git a/A.java b/A.java\n--- /dev/null\n+++ b/A.java\n@@ -0,0 +1,1 @@\n+class A {}",
                "{\"status\":\"VALIDATED\"}", now
        ));

        SandboxExecution executionA = sandboxExecutionRepository.saveAndFlush(new SandboxExecution(
                changeA, graphA.task(), graphA.organization(), graphA.repository(),
                SandboxExecutionProfile.JAVA_MAVEN_TEST, now
        ));

        Approval approvalA = approvalRepository.saveAndFlush(new Approval(
                changeA, graphA.task(), graphA.organization(), graphA.repository(),
                changeA.getVersion(), executionA, graphA.user(), now
        ));

        GithubMutation mutationA = githubMutationRepository.saveAndFlush(new GithubMutation(
                graphA.organization(), graphA.repository(), graphA.task(),
                changeA, changeA.getVersion(), approvalA, executionA,
                "baseA", "devflow/task-a/change-a", now
        ));

        // Cross-org lookup fails
        assertThat(githubMutationRepository.findByIdAndOrganizationId(mutationA.getId(), graphB.organization().getId())).isEmpty();
        assertThat(githubMutationRepository.findByIdAndOrganizationIdWithDetails(mutationA.getId(), graphB.organization().getId())).isEmpty();

        // Same-org lookup succeeds
        assertThat(githubMutationRepository.findByIdAndOrganizationId(mutationA.getId(), graphA.organization().getId())).isPresent();
        assertThat(githubMutationRepository.findByIdAndOrganizationIdWithDetails(mutationA.getId(), graphA.organization().getId())).isPresent();
    }

    @Test
    void enforcesUniqueGithubMutationPerProposalVersion() {
        PersistedGraph graph = persistGraph("dup-mut@example.com", "dup-mut-auth", "dup-mut-org", "dup-mut-repo");
        Instant now = Instant.now();

        ProposedChange change = proposedChangeRepository.saveAndFlush(new ProposedChange(
                graph.task(), graph.organization(), graph.repository(),
                com.devflow.api.proposedchange.domain.ProposedChangeStatus.VALIDATED,
                "Dup mutation change", "Description", "main",
                "diff --git a/D.java b/D.java\n--- /dev/null\n+++ b/D.java\n@@ -0,0 +1,1 @@\n+class D {}",
                "{\"status\":\"VALIDATED\"}", now
        ));

        SandboxExecution execution = sandboxExecutionRepository.saveAndFlush(new SandboxExecution(
                change, graph.task(), graph.organization(), graph.repository(),
                SandboxExecutionProfile.JAVA_MAVEN_TEST, now
        ));

        Approval approval = approvalRepository.saveAndFlush(new Approval(
                change, graph.task(), graph.organization(), graph.repository(),
                change.getVersion(), execution, graph.user(), now
        ));

        githubMutationRepository.saveAndFlush(new GithubMutation(
                graph.organization(), graph.repository(), graph.task(),
                change, change.getVersion(), approval, execution,
                "baseD", "devflow/task-d/change-d", now
        ));

        // Duplicate mutation for the same organization, repository, proposed change, and version must violate unique constraint
        assertThatThrownBy(() -> githubMutationRepository.saveAndFlush(new GithubMutation(
                graph.organization(), graph.repository(), graph.task(),
                change, change.getVersion(), approval, execution,
                "baseD", "devflow/task-d/change-d", now
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private record PersistedGraph(

            User user,
            Organization organization,
            OrganizationMember member,
            Repository repository,
            Task task) {
    }
}
