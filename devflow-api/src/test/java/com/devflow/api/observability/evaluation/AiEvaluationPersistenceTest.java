package com.devflow.api.observability.evaluation;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.persistence.organization.OrganizationRepository;
import com.devflow.api.persistence.repository.RepositoryRepository;
import com.devflow.api.persistence.task.TaskRepository;
import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskSourceType;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.user.domain.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiEvaluationPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private AiEvaluationRepository aiEvaluationRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private com.devflow.api.persistence.proposedchange.ProposedChangeRepository proposedChangeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Persists and updates AiEvaluation with optimistic locking and entity links")
    void persistsAndUpdatesAiEvaluation() {
        Instant now = Instant.now();
        Organization org = organizationRepository.saveAndFlush(new Organization("Evaluation Org", "eval-org", now, now));
        User user = userRepository.saveAndFlush(new User("eval@example.com", "Eval User", "ext-eval-auth", true, now, now));
        Repository repo = repositoryRepository.saveAndFlush(new Repository(
                org, RepositoryProvider.GITHUB, "repo-ext-1001", "devflow/eval-repo", "main", true, now, now));
        Task task = taskRepository.saveAndFlush(new Task(
                org, repo, user, TaskSourceType.MANUAL, null,
                "Test objective", "Objective details", TaskStatus.RUNNING,
                "task/eval-branch", "running", null, now, now, null, null));

        com.devflow.api.proposedchange.domain.ProposedChange change = proposedChangeRepository.saveAndFlush(
                new com.devflow.api.proposedchange.domain.ProposedChange(
                        task, org, repo,
                        com.devflow.api.proposedchange.domain.ProposedChangeStatus.VALIDATED,
                        "Evaluation change", "Change description", "main",
                        "diff --git a/File.java b/File.java\n--- /dev/null\n+++ b/File.java\n@@ -0,0 +1,1 @@\n+class File {}",
                        "{\"status\":\"VALIDATED\"}", now));

        UUID executionId = UUID.randomUUID();
        Instant start = Instant.now().minusSeconds(10);
        AiEvaluation evaluation = new AiEvaluation(
                UUID.randomUUID(),
                org,
                task,
                executionId,
                "openai-compatible",
                "gpt-4o",
                start,
                3,
                5,
                "RUNNING");

        AiEvaluation saved = aiEvaluationRepository.saveAndFlush(evaluation);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);

        // Update with completion
        Instant end = Instant.now();
        saved.complete(end, "COMPLETED", null);
        saved.linkProposal(change.getId());

        aiEvaluationRepository.saveAndFlush(saved);
        entityManager.clear();

        Optional<AiEvaluation> loaded = aiEvaluationRepository.findByExecutionId(executionId);
        assertThat(loaded).isPresent();
        AiEvaluation eval = loaded.get();
        assertThat(eval.getFinalState()).isEqualTo("COMPLETED");
        assertThat(eval.getFailureCode()).isNull();
        assertThat(eval.getDurationMs()).isGreaterThan(0L);
        assertThat(eval.getProposedChangeId()).isEqualTo(change.getId());
        assertThat(eval.getSandboxExecutionId()).isNull();
        assertThat(eval.getApprovalId()).isNull();
        assertThat(eval.getGithubMutationId()).isNull();
        assertThat(eval.getVersion()).isEqualTo(1L);

        List<AiEvaluation> taskEvals = aiEvaluationRepository.findByTaskIdOrderByStartedAtDesc(task.getId());
        assertThat(taskEvals).hasSize(1);
        assertThat(taskEvals.get(0).getExecutionId()).isEqualTo(executionId);
    }
}
