package com.devflow.api.async.job;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Internal-only wire format. It is never accepted by an HTTP endpoint.
 */
public record JobEnvelope(
        int schemaVersion,
        JobType type,
        UUID jobId,
        UUID eventId,
        int attempt,
        Instant createdAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int MAX_ATTEMPT = 1000;

    @JsonCreator
    public JobEnvelope(
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("type") JobType type,
            @JsonProperty("jobId") UUID jobId,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("attempt") int attempt,
            @JsonProperty("createdAt") Instant createdAt) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION || type == null || jobId == null || eventId == null
                || attempt < 1 || attempt > MAX_ATTEMPT || createdAt == null) {
            throw new IllegalArgumentException("Invalid async job envelope");
        }
        this.schemaVersion = schemaVersion;
        this.type = type;
        this.jobId = jobId;
        this.eventId = eventId;
        this.attempt = attempt;
        this.createdAt = createdAt;
    }

    public static JobEnvelope webhook(UUID jobId, UUID eventId, int attempt) {
        return new JobEnvelope(CURRENT_SCHEMA_VERSION, JobType.WEBHOOK_PROCESSING,
                jobId, eventId, attempt, Instant.now());
    }

    public static JobEnvelope sandbox(UUID jobId, UUID executionId, int attempt) {
        return new JobEnvelope(CURRENT_SCHEMA_VERSION, JobType.SANDBOX_EXECUTION,
                jobId, executionId, attempt, Instant.now());
    }

    public static JobEnvelope githubPr(UUID jobId, UUID mutationId, int attempt) {
        return new JobEnvelope(CURRENT_SCHEMA_VERSION, JobType.GITHUB_PR_CREATION,
                jobId, mutationId, attempt, Instant.now());
    }
}
