package com.devflow.api.observability.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Utility for attaching bounded, structured contextual metadata to the SLF4J MDC
 * during task, worker, and controller execution, ensuring that all attached keys
 * are reliably cleaned up when closed.
 */
public final class LoggingContext implements AutoCloseable {

    public static final String KEY_REQUEST_ID = "requestId";
    public static final String KEY_ORGANIZATION_ID = "organizationId";
    public static final String KEY_REPOSITORY_ID = "repositoryId";
    public static final String KEY_TASK_ID = "taskId";
    public static final String KEY_JOB_ID = "jobId";
    public static final String KEY_APPROVAL_ID = "approvalId";
    public static final String KEY_MUTATION_ID = "mutationId";
    public static final String KEY_PROPOSAL_ID = "proposedChangeId";

    private final Map<String, String> previousValues = new HashMap<>();

    private LoggingContext() {
    }

    public static LoggingContext init() {
        return new LoggingContext();
    }

    public LoggingContext with(String key, String value) {
        if (key != null && value != null && !value.isBlank()) {
            if (!previousValues.containsKey(key)) {
                previousValues.put(key, MDC.get(key));
            }
            MDC.put(key, value);
        }
        return this;
    }

    public LoggingContext with(String key, UUID value) {
        if (value != null) {
            with(key, value.toString());
        }
        return this;
    }

    public LoggingContext withOrganizationId(UUID organizationId) {
        return with(KEY_ORGANIZATION_ID, organizationId);
    }

    public LoggingContext withRepositoryId(UUID repositoryId) {
        return with(KEY_REPOSITORY_ID, repositoryId);
    }

    public LoggingContext withTaskId(UUID taskId) {
        return with(KEY_TASK_ID, taskId);
    }

    public LoggingContext withJobId(UUID jobId) {
        return with(KEY_JOB_ID, jobId);
    }

    public LoggingContext withApprovalId(UUID approvalId) {
        return with(KEY_APPROVAL_ID, approvalId);
    }

    public LoggingContext withMutationId(UUID mutationId) {
        return with(KEY_MUTATION_ID, mutationId);
    }

    public LoggingContext withProposalId(UUID proposalId) {
        return with(KEY_PROPOSAL_ID, proposalId);
    }

    @Override
    public void close() {
        for (Map.Entry<String, String> entry : previousValues.entrySet()) {
            if (entry.getValue() != null) {
                MDC.put(entry.getKey(), entry.getValue());
            } else {
                MDC.remove(entry.getKey());
            }
        }
        previousValues.clear();
    }
}
