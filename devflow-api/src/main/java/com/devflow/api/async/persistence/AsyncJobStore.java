package com.devflow.api.async.persistence;

import com.devflow.api.async.config.AsyncJobProperties;
import com.devflow.api.async.job.JobEnvelope;
import com.devflow.api.async.job.JobType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AsyncJobStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AsyncJobProperties properties;

    public AsyncJobStore(JdbcTemplate jdbc, ObjectMapper objectMapper, AsyncJobProperties properties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public UUID enqueueWebhook(UUID eventId) {
        UUID jobId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        JobEnvelope envelope = JobEnvelope.webhook(jobId, eventId, 1);
        String payload = write(envelope);
        jdbc.update("""
                INSERT INTO async_jobs
                    (id, job_type, status, attempts, max_attempts, available_at, created_at, updated_at)
                VALUES (?, ?, 'PENDING', 0, ?, now(), now(), now())
                """, jobId, JobType.WEBHOOK_PROCESSING.name(),
                properties.getWorker().getMaxAttempts());
        jdbc.update("""
                INSERT INTO async_job_outbox
                    (id, job_id, job_type, payload, status, available_at, created_at, updated_at)
                VALUES (?, ?, ?, ?::jsonb, 'PENDING', now(), now(), now())
                """, outboxId, jobId, JobType.WEBHOOK_PROCESSING.name(), payload);
        return jobId;
    }

    @Transactional
    public UUID enqueueSandboxExecution(UUID executionId) {
        UUID jobId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        JobEnvelope envelope = JobEnvelope.sandbox(jobId, executionId, 1);
        String payload = write(envelope);
        jdbc.update("""
                INSERT INTO async_jobs
                    (id, job_type, status, attempts, max_attempts, available_at, created_at, updated_at)
                VALUES (?, ?, 'PENDING', 0, ?, now(), now(), now())
                """, jobId, JobType.SANDBOX_EXECUTION.name(),
                properties.getWorker().getMaxAttempts());
        jdbc.update("""
                INSERT INTO async_job_outbox
                    (id, job_id, job_type, payload, status, available_at, created_at, updated_at)
                VALUES (?, ?, ?, ?::jsonb, 'PENDING', now(), now(), now())
                """, outboxId, jobId, JobType.SANDBOX_EXECUTION.name(), payload);
        return jobId;
    }

    @Transactional
    public UUID enqueueGithubPrCreation(UUID mutationId) {
        UUID jobId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        JobEnvelope envelope = JobEnvelope.githubPr(jobId, mutationId, 1);
        String payload = write(envelope);
        jdbc.update("""
                INSERT INTO async_jobs
                    (id, job_type, status, attempts, max_attempts, available_at, created_at, updated_at)
                VALUES (?, ?, 'PENDING', 0, ?, now(), now(), now())
                """, jobId, JobType.GITHUB_PR_CREATION.name(),
                properties.getWorker().getMaxAttempts());
        jdbc.update("""
                INSERT INTO async_job_outbox
                    (id, job_id, job_type, payload, status, available_at, created_at, updated_at)
                VALUES (?, ?, ?, ?::jsonb, 'PENDING', now(), now(), now())
                """, outboxId, jobId, JobType.GITHUB_PR_CREATION.name(), payload);
        return jobId;
    }


    @Transactional
    public List<OutboxRecord> claimOutbox(int limit) {
        recoverStaleClaims(Duration.ofSeconds(properties.getOutbox().getClaimTimeoutSeconds()));
        return jdbc.query("""
                WITH claimed AS (
                    SELECT id
                    FROM async_job_outbox
                    WHERE status = 'PENDING' AND available_at <= now()
                    ORDER BY created_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE async_job_outbox o
                   SET status = 'PUBLISHING',
                       publish_attempts = o.publish_attempts + 1,
                       claimed_at = now(),
                       updated_at = now()
                  FROM claimed
                 WHERE o.id = claimed.id
                RETURNING o.id, o.job_id, o.job_type, o.payload::text, o.publish_attempts
                """, this::mapOutbox, Math.max(1, limit));
    }

    @Transactional
    public void recoverStaleClaims(Duration timeout) {
        jdbc.update("""
                UPDATE async_job_outbox
                   SET status = 'PENDING', claimed_at = NULL, updated_at = now()
                 WHERE status = 'PUBLISHING'
                   AND claimed_at < now() - (? * interval '1 millisecond')
                """, Math.max(1, timeout.toMillis()));
    }

    @Transactional
    public void markPublished(UUID outboxId) {
        jdbc.update("""
                UPDATE async_job_outbox
                   SET status = 'PUBLISHED', published_at = now(), claimed_at = NULL,
                       last_error = NULL, updated_at = now()
                 WHERE id = ? AND status = 'PUBLISHING'
                """, outboxId);
    }

    @Transactional
    public void releaseOutbox(UUID outboxId, String error, long delayMs) {
        jdbc.update("""
                UPDATE async_job_outbox
                   SET status = 'PENDING', available_at = now() + (? * interval '1 millisecond'),
                       claimed_at = NULL, last_error = left(?, 2000), updated_at = now()
                 WHERE id = ? AND status = 'PUBLISHING'
                """, Math.max(1, delayMs), error == null ? "publish failed" : error, outboxId);
    }

    @Transactional
    public void markOutboxFailed(UUID outboxId, String error) {
        jdbc.update("""
                UPDATE async_jobs
                   SET status = 'FAILED', completed_at = now(), last_error = left(?, 2000), updated_at = now()
                 WHERE id = (SELECT job_id FROM async_job_outbox WHERE id = ?)
                """, error == null ? "invalid outbox message" : error, outboxId);
        jdbc.update("""
                UPDATE async_job_outbox
                   SET status = 'FAILED', claimed_at = NULL, last_error = left(?, 2000), updated_at = now()
                 WHERE id = ? AND status = 'PUBLISHING'
                """, error == null ? "invalid outbox message" : error, outboxId);
    }

    @Transactional
    public JobState claimJob(UUID jobId) {
        List<JobState> claimed = jdbc.query("""
                UPDATE async_jobs
                   SET status = 'PROCESSING', attempts = attempts + 1,
                       started_at = now(), updated_at = now()
                 WHERE id = ? AND status = 'PENDING' AND available_at <= now()
                RETURNING status, attempts, max_attempts
                """, (rs, rowNum) -> new JobState(
                JobStatus.PROCESSING, rs.getInt("attempts"), rs.getInt("max_attempts"), true), jobId);
        if (!claimed.isEmpty()) {
            return claimed.get(0);
        }
        return jdbc.query("""
                SELECT status, attempts, max_attempts
                  FROM async_jobs
                 WHERE id = ?
                """, (rs, rowNum) -> new JobState(
                JobStatus.valueOf(rs.getString("status")), rs.getInt("attempts"),
                rs.getInt("max_attempts"), false), jobId).stream().findFirst().orElse(JobState.UNKNOWN);
    }

    @Transactional
    public void recoverStaleJobs(Duration timeout) {
        jdbc.update("""
                UPDATE async_jobs
                   SET status = 'PENDING', available_at = now(), updated_at = now()
                 WHERE status = 'PROCESSING'
                   AND started_at < now() - (? * interval '1 millisecond')
                """, Math.max(1, timeout.toMillis()));
        jdbc.update("""
                UPDATE async_job_outbox o
                   SET status = 'PENDING', available_at = now(), claimed_at = NULL, updated_at = now()
                 WHERE o.status = 'PUBLISHED'
                   AND EXISTS (
                       SELECT 1 FROM async_jobs j
                        WHERE j.id = o.job_id AND j.status = 'PENDING'
                   )
                """);
    }

    @Transactional
    public void markSucceeded(UUID jobId) {
        jdbc.update("""
                UPDATE async_jobs
                   SET status = 'SUCCEEDED', completed_at = now(), updated_at = now(), last_error = NULL
                 WHERE id = ? AND status = 'PROCESSING'
                """, jobId);
    }

    @Transactional
    public void markFailedOrRetry(UUID jobId, String error, int attempts, int maxAttempts, long delayMs) {
        if (attempts >= maxAttempts) {
            jdbc.update("""
                    UPDATE async_jobs
                       SET status = 'FAILED', completed_at = now(), last_error = left(?, 2000),
                           updated_at = now()
                     WHERE id = ? AND status = 'PROCESSING'
                    """, error == null ? "job failed" : error, jobId);
            jdbc.update("""
                    UPDATE async_job_outbox
                       SET status = 'FAILED', last_error = left(?, 2000), updated_at = now()
                     WHERE job_id = ? AND status <> 'FAILED'
                    """, error == null ? "job failed" : error, jobId);
            return;
        }
        jdbc.update("""
                UPDATE async_jobs
                   SET status = 'PENDING', available_at = now() + (? * interval '1 millisecond'),
                       last_error = left(?, 2000), updated_at = now()
                 WHERE id = ? AND status = 'PROCESSING'
                """, Math.max(1, delayMs), error == null ? "job failed" : error, jobId);
        jdbc.update("""
                UPDATE async_job_outbox
                   SET status = 'PENDING', available_at = now() + (? * interval '1 millisecond'),
                       claimed_at = NULL, payload = payload || jsonb_build_object('attempt', ?),
                       last_error = left(?, 2000), updated_at = now()
                 WHERE job_id = ? AND status = 'PUBLISHED'
                """, Math.max(1, delayMs), attempts + 1, error == null ? "job failed" : error, jobId);
    }

    private OutboxRecord mapOutbox(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("job_id", UUID.class),
                JobType.valueOf(rs.getString("job_type")),
                rs.getString("payload"),
                rs.getInt("publish_attempts"));
    }

    private String write(JobEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize async job envelope", exception);
        }
    }

    public record OutboxRecord(UUID outboxId, UUID jobId, JobType type, String payload, int publishAttempts) {
    }

    public record JobState(JobStatus status, int attempts, int maxAttempts, boolean claimed) {
        public static final JobState UNKNOWN = new JobState(JobStatus.UNKNOWN, 0, 0, false);
    }

    public enum JobStatus {
        PENDING, PROCESSING, SUCCEEDED, FAILED, UNKNOWN
    }
}
