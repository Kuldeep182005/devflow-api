package com.devflow.api.observability.metrics;

import com.devflow.api.async.job.JobType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Centralized, bounded Micrometer metrics recorder for DevFlow API.
 * <p>
 * Invariant: Never use high-cardinality tags (such as taskId, userId, repositoryId,
 * organizationId, requestId, mutationId). All tag dimensions MUST be bounded.
 */
@Component
public class DevFlowMetrics {

    private final MeterRegistry registry;

    public DevFlowMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    // --- Task Metrics ---

    public void recordTaskCreated(String sourceType) {
        Counter.builder("devflow.tasks.created")
                .description("Number of tasks created")
                .tag("source_type", sanitize(sourceType, "UNKNOWN"))
                .register(registry)
                .increment();
    }

    public void recordTaskTransition(String fromStatus, String toStatus) {
        Counter.builder("devflow.tasks.transition")
                .description("Task status state machine transitions")
                .tag("from_status", sanitize(fromStatus, "NONE"))
                .tag("to_status", sanitize(toStatus, "UNKNOWN"))
                .register(registry)
                .increment();
    }

    public void recordTaskFailure(String failureCode) {
        Counter.builder("devflow.tasks.failures")
                .description("Number of task execution failures")
                .tag("failure_code", sanitize(failureCode, "UNKNOWN"))
                .register(registry)
                .increment();
    }

    public void recordTaskDuration(Duration duration, String status) {
        Timer.builder("devflow.tasks.duration")
                .description("Task execution duration from start to finish")
                .tag("status", sanitize(status, "UNKNOWN"))
                .register(registry)
                .record(duration);
    }

    // --- Worker Metrics ---

    public void recordJobReceived(JobType type) {
        Counter.builder("devflow.worker.jobs.received")
                .description("Number of async jobs claimed by workers")
                .tag("type", type != null ? type.name() : "UNKNOWN")
                .register(registry)
                .increment();
    }

    public void recordJobSucceeded(JobType type, Duration duration) {
        Counter.builder("devflow.worker.jobs.succeeded")
                .description("Number of async jobs completed successfully")
                .tag("type", type != null ? type.name() : "UNKNOWN")
                .register(registry)
                .increment();

        Timer.builder("devflow.worker.jobs.duration")
                .description("Async job processing duration")
                .tag("type", type != null ? type.name() : "UNKNOWN")
                .tag("status", "SUCCESS")
                .register(registry)
                .record(duration);
    }

    public void recordJobFailed(JobType type, String failureCode, Duration duration) {
        Counter.builder("devflow.worker.jobs.failed")
                .description("Number of async job execution failures")
                .tag("type", type != null ? type.name() : "UNKNOWN")
                .tag("failure_code", sanitize(failureCode, "UNKNOWN"))
                .register(registry)
                .increment();

        if (duration != null) {
            Timer.builder("devflow.worker.jobs.duration")
                    .description("Async job processing duration")
                    .tag("type", type != null ? type.name() : "UNKNOWN")
                    .tag("status", "FAILED")
                    .register(registry)
                    .record(duration);
        }
    }

    public void recordJobRetried(JobType type) {
        Counter.builder("devflow.worker.jobs.retried")
                .description("Number of async jobs scheduled for retry")
                .tag("type", type != null ? type.name() : "UNKNOWN")
                .register(registry)
                .increment();
    }

    public void recordStaleClaimRecovered() {
        Counter.builder("devflow.worker.stale_claims_recovered")
                .description("Number of stale async job claims recovered")
                .register(registry)
                .increment();
    }

    // --- LLM Metrics ---

    public void recordLlmRequest(String provider, String model, String status, Duration duration) {
        Counter.builder("devflow.llm.requests")
                .description("LLM completions requested")
                .tag("provider", sanitize(provider, "UNKNOWN"))
                .tag("model", sanitize(model, "DEFAULT"))
                .tag("status", sanitize(status, "UNKNOWN"))
                .register(registry)
                .increment();

        if (duration != null) {
            Timer.builder("devflow.llm.duration")
                    .description("LLM request latency")
                    .tag("provider", sanitize(provider, "UNKNOWN"))
                    .tag("model", sanitize(model, "DEFAULT"))
                    .register(registry)
                    .record(duration);
        }
    }

    public void recordLlmFailure(String provider, String category) {
        Counter.builder("devflow.llm.failures")
                .description("LLM request errors")
                .tag("provider", sanitize(provider, "UNKNOWN"))
                .tag("category", sanitize(category, "UNKNOWN"))
                .register(registry)
                .increment();
    }

    // --- Repository Tool Metrics ---

    public void recordRepoToolCall(String tool, String status, Duration duration) {
        Counter.builder("devflow.repo_tools.calls")
                .description("Repository tool executions")
                .tag("tool", sanitize(tool, "UNKNOWN"))
                .tag("status", sanitize(status, "UNKNOWN"))
                .register(registry)
                .increment();

        if (duration != null) {
            Timer.builder("devflow.repo_tools.duration")
                    .description("Repository tool execution duration")
                    .tag("tool", sanitize(tool, "UNKNOWN"))
                    .register(registry)
                    .record(duration);
        }
    }

    public void recordRepoToolResourceLimit(String tool, String limitType) {
        Counter.builder("devflow.repo_tools.resource_limit_rejected")
                .description("Repository tool calls rejected due to resource limits")
                .tag("tool", sanitize(tool, "UNKNOWN"))
                .tag("limit_type", sanitize(limitType, "UNKNOWN"))
                .register(registry)
                .increment();
    }

    // --- Sandbox Metrics ---

    public void recordSandboxExecution(String profile, String status, Duration duration) {
        Counter.builder("devflow.sandbox.executions")
                .description("Sandbox test container executions")
                .tag("profile", sanitize(profile, "DEFAULT"))
                .tag("status", sanitize(status, "UNKNOWN"))
                .register(registry)
                .increment();

        if (duration != null) {
            Timer.builder("devflow.sandbox.duration")
                    .description("Sandbox execution duration")
                    .tag("profile", sanitize(profile, "DEFAULT"))
                    .tag("status", sanitize(status, "UNKNOWN"))
                    .register(registry)
                    .record(duration);
        }
    }

    public void recordSandboxFailure(String profile, String failureCode) {
        Counter.builder("devflow.sandbox.failures")
                .description("Sandbox execution failures")
                .tag("profile", sanitize(profile, "DEFAULT"))
                .tag("failure_code", sanitize(failureCode, "UNKNOWN"))
                .register(registry)
                .increment();
    }

    // --- Approvals Metrics ---

    public void recordApprovalDecision(String decision) {
        Counter.builder("devflow.approvals.decisions")
                .description("Human approval decisions recorded")
                .tag("decision", sanitize(decision, "UNKNOWN"))
                .register(registry)
                .increment();
    }

    public void recordStaleApproval() {
        Counter.builder("devflow.approvals.stale")
                .description("Approvals rendered stale by proposal updates")
                .register(registry)
                .increment();
    }

    // --- GitHub Mutation Metrics ---

    public void recordGithubMutationAttempt() {
        Counter.builder("devflow.github.mutations.attempts")
                .description("GitHub mutation workflow attempts")
                .register(registry)
                .increment();
    }

    public void recordGithubMutationSuccess(Duration duration) {
        Counter.builder("devflow.github.mutations.succeeded")
                .description("GitHub mutation workflows succeeded")
                .register(registry)
                .increment();

        if (duration != null) {
            Timer.builder("devflow.github.mutations.duration")
                    .description("GitHub mutation workflow duration")
                    .tag("status", "SUCCEEDED")
                    .register(registry)
                    .record(duration);
        }
    }

    public void recordGithubBranchCreated() {
        Counter.builder("devflow.github.mutations.branch_created")
                .description("GitHub deterministic branches created")
                .register(registry)
                .increment();
    }

    public void recordGithubCommitCreated() {
        Counter.builder("devflow.github.mutations.commit_created")
                .description("GitHub atomic commits created")
                .register(registry)
                .increment();
    }

    public void recordGithubPrCreated() {
        Counter.builder("devflow.github.mutations.pr_created")
                .description("GitHub pull requests created")
                .register(registry)
                .increment();
    }

    public void recordGithubMutationFailed(String failureCode) {
        Counter.builder("devflow.github.mutations.failed")
                .description("GitHub mutation workflow failures")
                .tag("failure_code", sanitize(failureCode, "UNKNOWN"))
                .register(registry)
                .increment();
    }

    public void recordGithubMutationStaleBase() {
        Counter.builder("devflow.github.mutations.stale_base")
                .description("GitHub mutations aborted due to base revision drift")
                .register(registry)
                .increment();
    }

    public void recordGithubReconciliation(String outcome) {
        Counter.builder("devflow.github.mutations.reconciled")
                .description("GitHub mutation idempotency reconciliations")
                .tag("outcome", sanitize(outcome, "UNKNOWN"))
                .register(registry)
                .increment();
    }

    // --- Security Metrics ---

    public void recordSecurityEvent(String eventType, String category) {
        Counter.builder("devflow.security.events")
                .description("Security boundary events")
                .tag("event_type", sanitize(eventType, "UNKNOWN"))
                .tag("category", sanitize(category, "UNKNOWN"))
                .register(registry)
                .increment();
    }

    // --- AI Evaluation / Outcome Metrics ---

    public void recordAiAgentCompletion(String outcome) {
        Counter.builder("devflow.ai.eval.agent_completed")
                .description("Agent execution completions")
                .tag("outcome", sanitize(outcome, "UNKNOWN"))
                .register(registry)
                .increment();
    }

    public void recordAiProposalProduced() {
        Counter.builder("devflow.ai.eval.proposals_produced")
                .description("Validated ProposedChange patches produced")
                .register(registry)
                .increment();
    }

    public void recordAiSandboxPassed() {
        Counter.builder("devflow.ai.eval.sandbox_passed")
                .description("Sandbox executions that passed")
                .register(registry)
                .increment();
    }

    public void recordAiSandboxFailed() {
        Counter.builder("devflow.ai.eval.sandbox_failed")
                .description("Sandbox executions that failed")
                .register(registry)
                .increment();
    }

    public void recordAiApprovalGranted() {
        Counter.builder("devflow.ai.eval.approvals_granted")
                .description("Human approvals granted")
                .register(registry)
                .increment();
    }

    public void recordAiPrCreated() {
        Counter.builder("devflow.ai.eval.prs_created")
                .description("Pull requests successfully created from approved proposals")
                .register(registry)
                .increment();
    }

    private String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
    }
}
