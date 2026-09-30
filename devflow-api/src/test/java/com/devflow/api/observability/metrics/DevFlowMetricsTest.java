package com.devflow.api.observability.metrics;

import com.devflow.api.async.job.JobType;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DevFlowMetricsTest {

    private SimpleMeterRegistry registry;
    private DevFlowMetrics metrics;

    private static final Set<String> FORBIDDEN_HIGH_CARDINALITY_TAGS = Set.of(
            "taskid", "task_id",
            "userid", "user_id",
            "repositoryid", "repository_id",
            "organizationid", "organization_id",
            "requestid", "request_id",
            "mutationid", "mutation_id",
            "approvalid", "approval_id",
            "jobid", "job_id"
    );

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new DevFlowMetrics(registry);
    }

    @Test
    @DisplayName("Records task metrics without high cardinality tags")
    void recordsTaskMetrics() {
        metrics.recordTaskCreated("API");
        metrics.recordTaskTransition("NEW", "RUNNING");
        metrics.recordTaskFailure("TIMEOUT");
        metrics.recordTaskDuration(Duration.ofMillis(250), "FAILED");

        assertNoHighCardinalityTags();
        assertThat(registry.find("devflow.tasks.created").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.tasks.transition").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.tasks.failures").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.tasks.duration").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Records worker job metrics without high cardinality tags")
    void recordsWorkerMetrics() {
        metrics.recordJobReceived(JobType.WEBHOOK_PROCESSING);
        metrics.recordJobSucceeded(JobType.WEBHOOK_PROCESSING, Duration.ofMillis(50));
        metrics.recordJobFailed(JobType.GITHUB_PR_CREATION, "EXECUTION_ERROR", Duration.ofMillis(100));
        metrics.recordJobRetried(JobType.GITHUB_PR_CREATION);
        metrics.recordStaleClaimRecovered();

        assertNoHighCardinalityTags();
        assertThat(registry.find("devflow.worker.jobs.received").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.worker.jobs.succeeded").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.worker.jobs.failed").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.worker.jobs.retried").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.worker.stale_claims_recovered").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Records LLM and tool metrics without high cardinality tags")
    void recordsLlmAndToolMetrics() {
        metrics.recordLlmRequest("openai-compatible", "gpt-4o", "SUCCESS", Duration.ofMillis(300));
        metrics.recordLlmFailure("openai-compatible", "RATE_LIMIT");

        metrics.recordRepoToolCall("read_file", "SUCCESS", Duration.ofMillis(20));
        metrics.recordRepoToolResourceLimit("read_file", "MAX_BYTES");

        metrics.recordSandboxExecution("DEFAULT", "PASSED", Duration.ofMillis(500));
        metrics.recordSandboxFailure("DEFAULT", "TIMEOUT");

        assertNoHighCardinalityTags();
        assertThat(registry.find("devflow.llm.requests").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.llm.failures").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.repo_tools.calls").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.repo_tools.resource_limit_rejected").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.sandbox.executions").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.sandbox.failures").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Records security metrics without sensitive or high cardinality dimensions")
    void recordsSecurityMetrics() {
        metrics.recordSecurityEvent("auth_failure", "invalid_jwt");
        metrics.recordSecurityEvent("access_denied", "idor_attempt");
        metrics.recordSecurityEvent("webhook_signature_failure", "invalid_secret");
        metrics.recordSecurityEvent("ratelimit_exceeded", "auth_login");

        assertNoHighCardinalityTags();
        assertThat(registry.find("devflow.security.events").counters()).hasSize(4);
    }

    @Test
    @DisplayName("Records AI evaluation outcome metrics")
    void recordsAiEvaluationMetrics() {
        metrics.recordAiAgentCompletion("COMPLETED");
        metrics.recordAiProposalProduced();
        metrics.recordAiSandboxPassed();
        metrics.recordAiSandboxFailed();
        metrics.recordAiApprovalGranted();
        metrics.recordAiPrCreated();

        assertNoHighCardinalityTags();
        assertThat(registry.find("devflow.ai.eval.agent_completed").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.ai.eval.proposals_produced").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.ai.eval.sandbox_passed").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.ai.eval.sandbox_failed").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.ai.eval.approvals_granted").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devflow.ai.eval.prs_created").counter().count()).isEqualTo(1.0);
    }

    private void assertNoHighCardinalityTags() {
        for (Meter meter : registry.getMeters()) {
            for (Tag tag : meter.getId().getTags()) {
                String key = tag.getKey().toLowerCase().replace("-", "").replace("_", "");
                assertThat(FORBIDDEN_HIGH_CARDINALITY_TAGS)
                        .withFailMessage("Meter '%s' has forbidden high-cardinality tag key '%s'",
                                meter.getId().getName(), tag.getKey())
                        .doesNotContain(key);
            }
        }
    }
}
