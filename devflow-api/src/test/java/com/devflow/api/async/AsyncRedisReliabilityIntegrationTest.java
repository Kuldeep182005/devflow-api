package com.devflow.api.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devflow.api.async.config.AsyncJobProperties;
import com.devflow.api.async.job.JobEnvelope;
import com.devflow.api.async.job.JobType;
import com.devflow.api.async.outbox.AsyncJobPublisher;
import com.devflow.api.async.persistence.AsyncJobStore;
import com.devflow.api.async.worker.AsyncJobWorker;
import com.devflow.api.async.worker.WebhookProcessingHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AsyncRedisReliabilityIntegrationTest {

    @Container
    static final GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine");

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static ObjectMapper objectMapper;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void connectToRedis() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redisContainer.getHost(), redisContainer.getFirstMappedPort()));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgresContainer.getJdbcUrl(),
                postgresContainer.getUsername(),
                postgresContainer.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void closeRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void workerCreatesGroupProcessesXaddAndAcknowledgesWithXack() throws Exception {
        String stream = stream();
        String group = group();
        UUID jobId = UUID.randomUUID();
        AsyncJobStore store = mockStore(jobId, 1, 3, true);
        AtomicInteger processed = new AtomicInteger();
        AsyncJobWorker worker = worker(store, job -> processed.incrementAndGet(),
                properties(stream, group, "consumer-1", 1));
        add(stream, JobEnvelope.webhook(jobId, UUID.randomUUID(), 1));

        worker.start();
        try {
            await(() -> processed.get() == 1);
            await(() -> pending(stream, group) == 0);
            verify(store).markSucceeded(jobId);
        } finally {
            worker.stop();
        }
    }

    @Test
    void consumersInOneGroupDistributeMessages() throws Exception {
        String stream = stream();
        String group = group();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        AsyncJobStore store = mock(AsyncJobStore.class);
        when(store.claimJob(any())).thenReturn(
                new AsyncJobStore.JobState(AsyncJobStore.JobStatus.PROCESSING, 1, 3, true));
        doNothing().when(store).recoverStaleJobs(any());
        AtomicInteger firstConsumer = new AtomicInteger();
        AtomicInteger secondConsumer = new AtomicInteger();
        List<UUID> firstJobs = new ArrayList<>();
        List<UUID> secondJobs = new ArrayList<>();
        CountDownLatch bothConsumers = new CountDownLatch(2);
        AsyncJobWorker workerOne = worker(store, job -> {
            firstConsumer.incrementAndGet();
            firstJobs.add(job.jobId());
            bothConsumers.countDown();
            bothConsumers.await(5, TimeUnit.SECONDS);
        },
                properties(stream, group, "consumer-1", 1));
        AsyncJobWorker workerTwo = worker(store, job -> {
            secondConsumer.incrementAndGet();
            secondJobs.add(job.jobId());
            bothConsumers.countDown();
            bothConsumers.await(5, TimeUnit.SECONDS);
        },
                properties(stream, group, "consumer-2", 1));
        add(stream, JobEnvelope.webhook(first, UUID.randomUUID(), 1));
        add(stream, JobEnvelope.webhook(second, UUID.randomUUID(), 1));

        workerOne.start();
        workerTwo.start();
        try {
            await(() -> firstConsumer.get() + secondConsumer.get() == 2);
            assertThat(firstJobs).hasSize(1);
            assertThat(secondJobs).hasSize(1);
            assertThat(firstJobs.get(0)).isNotEqualTo(secondJobs.get(0));
            assertThat(List.of(firstJobs.get(0), secondJobs.get(0)))
                    .containsExactlyInAnyOrder(first, second);
            assertThat(pending(stream, group)).isZero();
        } finally {
            workerOne.stop();
            workerTwo.stop();
        }
    }

    @Test
    void idlePendingMessageIsClaimedByAnotherConsumer() throws Exception {
        String stream = stream();
        String group = group();
        UUID jobId = UUID.randomUUID();
        AsyncJobStore unavailableStore = mock(AsyncJobStore.class);
        when(unavailableStore.claimJob(jobId))
                .thenThrow(new IllegalStateException("database outage"));
        doNothing().when(unavailableStore).recoverStaleJobs(any());
        AtomicInteger abandonedInvocations = new AtomicInteger();
        AsyncJobWorker abandonedWorker = worker(unavailableStore, job -> abandonedInvocations.incrementAndGet(),
                properties(stream, group, "consumer-abandoned", 1, 1000));
        add(stream, JobEnvelope.webhook(jobId, UUID.randomUUID(), 1));

        abandonedWorker.start();
        await(() -> pending(stream, group) == 1);
        abandonedWorker.stop();
        assertThat(abandonedInvocations).hasValue(0);
        Thread.sleep(1200);

        jdbc.update("""
                INSERT INTO async_jobs
                    (id, job_type, status, attempts, max_attempts, available_at)
                VALUES (?, ?, 'PENDING', 0, 3, now())
                """, jobId, JobType.WEBHOOK_PROCESSING.name());
        AsyncJobStore recoveryStore = realStore(properties(stream, group, "consumer-recovery", 1, 1000));
        AtomicInteger recoveredInvocations = new AtomicInteger();
        AsyncJobWorker recoveryWorker = worker(recoveryStore,
                job -> recoveredInvocations.incrementAndGet(),
                properties(stream, group, "consumer-recovery", 1, 1000));
        recoveryWorker.start();
        try {
            await(() -> recoveredInvocations.get() == 1);
            assertThat(recoveredInvocations).hasValue(1);
            await(() -> pending(stream, group) == 0);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM async_jobs WHERE id = ?", String.class, jobId))
                    .isEqualTo("SUCCEEDED");
        } finally {
            recoveryWorker.stop();
        }
    }

    @Test
    void duplicateStreamDeliveriesAreIdempotentFromDatabaseClaim() throws Exception {
        String stream = stream();
        String group = group();
        UUID jobId = UUID.randomUUID();
        AsyncJobStore store = mock(AsyncJobStore.class);
        AtomicBoolean firstClaim = new AtomicBoolean(true);
        when(store.claimJob(jobId)).thenAnswer(invocation -> firstClaim.getAndSet(false)
                ? new AsyncJobStore.JobState(AsyncJobStore.JobStatus.PROCESSING, 1, 3, true)
                : new AsyncJobStore.JobState(AsyncJobStore.JobStatus.SUCCEEDED, 1, 3, false));
        doNothing().when(store).recoverStaleJobs(any());
        AtomicInteger processed = new AtomicInteger();
        AsyncJobWorker worker = worker(store, job -> processed.incrementAndGet(),
                properties(stream, group, "consumer-1", 1));
        JobEnvelope envelope = JobEnvelope.webhook(jobId, UUID.randomUUID(), 1);
        add(stream, envelope);
        add(stream, envelope);

        worker.start();
        try {
            await(() -> pending(stream, group) == 0);
            assertThat(processed).hasValue(1);
            verify(store).markSucceeded(jobId);
        } finally {
            worker.stop();
        }
    }

    @Test
    void retryAndPermanentFailureAreRecordedWithoutBlockingOtherJobs() throws Exception {
        String stream = stream();
        String group = group();
        UUID retryJob = UUID.randomUUID();
        UUID permanentJob = UUID.randomUUID();
        AsyncJobStore store = mock(AsyncJobStore.class);
        when(store.claimJob(retryJob)).thenReturn(
                new AsyncJobStore.JobState(AsyncJobStore.JobStatus.PROCESSING, 1, 2, true));
        when(store.claimJob(permanentJob)).thenReturn(
                new AsyncJobStore.JobState(AsyncJobStore.JobStatus.PROCESSING, 2, 2, true));
        doNothing().when(store).recoverStaleJobs(any());
        AtomicInteger completed = new AtomicInteger();
        WebhookProcessingHandler handler = job -> {
            if (job.jobId().equals(retryJob) || job.jobId().equals(permanentJob)) {
                throw new IllegalStateException("handler failure");
            }
            completed.incrementAndGet();
        };
        AsyncJobWorker worker = worker(store, handler, properties(stream, group, "consumer-1", 2));
        add(stream, JobEnvelope.webhook(retryJob, UUID.randomUUID(), 1));
        add(stream, JobEnvelope.webhook(permanentJob, UUID.randomUUID(), 1));

        worker.start();
        try {
            await(() -> pending(stream, group) == 0);
            verify(store).markFailedOrRetry(eq(retryJob), eq("HandlerFailure"), eq(1), eq(2), anyLong());
            verify(store).markFailedOrRetry(eq(permanentJob), eq("HandlerFailure"), eq(2), eq(2), anyLong());
        } finally {
            worker.stop();
        }
    }

    @Test
    void malformedAndUnknownJobsAreAcknowledgedAndDoNotInvokeHandler() throws Exception {
        String stream = stream();
        String group = group();
        AsyncJobStore store = mock(AsyncJobStore.class);
        doNothing().when(store).recoverStaleJobs(any());
        WebhookProcessingHandler handler = mock(WebhookProcessingHandler.class);
        AsyncJobWorker worker = worker(store, handler, properties(stream, group, "consumer-1", 1));
        redis.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(Map.of("not", "an envelope")));

        worker.start();
        try {
            await(() -> pending(stream, group) == 0);
            verifyNoInteractions(handler);
        } finally {
            worker.stop();
        }
    }

    @Test
    void handlerConcurrencyNeverExceedsConfiguredBound() throws Exception {
        String stream = stream();
        String group = group();
        int messageCount = 8;
        AsyncJobStore store = mock(AsyncJobStore.class);
        when(store.claimJob(any())).thenReturn(
                new AsyncJobStore.JobState(AsyncJobStore.JobStatus.PROCESSING, 1, 3, true));
        doNothing().when(store).recoverStaleJobs(any());
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();
        AsyncJobWorker worker = worker(store, job -> {
            int now = active.incrementAndGet();
            maximum.accumulateAndGet(now, Math::max);
            Thread.sleep(50);
            active.decrementAndGet();
            processed.incrementAndGet();
        }, properties(stream, group, "consumer-1", 2));
        for (int i = 0; i < messageCount; i++) {
            add(stream, JobEnvelope.webhook(UUID.randomUUID(), UUID.randomUUID(), 1));
        }

        worker.start();
        try {
            await(() -> processed.get() == messageCount);
            assertThat(maximum).hasValueLessThanOrEqualTo(2);
            assertThat(pending(stream, group)).isZero();
        } finally {
            worker.stop();
        }
    }

    @Test
    void shutdownWaitsForInFlightHandlerBeforeCallback() throws Exception {
        String stream = stream();
        String group = group();
        UUID jobId = UUID.randomUUID();
        AsyncJobStore store = mockStore(jobId, 1, 3, true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AsyncJobWorker worker = worker(store, job -> {
            entered.countDown();
            release.await();
        }, properties(stream, group, "consumer-1", 1, 5000));
        add(stream, JobEnvelope.webhook(jobId, UUID.randomUUID(), 1));
        worker.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        ExecutorService stopper = Executors.newSingleThreadExecutor();
        try {
            stopper.submit(() -> worker.stop(stopped::countDown));
            Thread.sleep(100);
            assertThat(stopped.getCount()).isEqualTo(1);
            release.countDown();
            assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            stopper.shutdownNow();
        }
    }

    @Test
    void publisherWritesToRedisAndMarksOutboxOnlyAfterXadd() throws Exception {
        String stream = stream();
        AsyncJobProperties properties = properties(stream, group(), "publisher-test", 1);
        UUID jobId = UUID.randomUUID();
        AsyncJobStore.OutboxRecord record = outboxRecord(jobId);
        AsyncJobStore store = mock(AsyncJobStore.class);
        when(store.claimOutbox(25)).thenReturn(List.of(record));
        AsyncJobPublisher publisher = new AsyncJobPublisher(store, redis, objectMapper, properties);

        publisher.publishPending();

        assertThat(redis.opsForStream().size(stream)).isEqualTo(1);
        verify(store).markPublished(record.outboxId());
    }

    @Test
    void redisOutageReleasesClaimSoDurableOutboxCanRetry() throws Exception {
        String stream = stream();
        AsyncJobProperties properties = properties(stream, group(), "publisher-outage", 1);
        AsyncJobStore.OutboxRecord record = outboxRecord(UUID.randomUUID());
        AsyncJobStore store = mock(AsyncJobStore.class);
        when(store.claimOutbox(25)).thenReturn(List.of(record));
        LettuceConnectionFactory unavailableFactory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        unavailableFactory.afterPropertiesSet();
        StringRedisTemplate unavailableRedis = new StringRedisTemplate(unavailableFactory);
        unavailableRedis.afterPropertiesSet();
        try {
            new AsyncJobPublisher(store, unavailableRedis, objectMapper, properties).publishPending();
            verify(store).releaseOutbox(eq(record.outboxId()), any(String.class),
                    eq(properties.getOutbox().getRetryDelayMs()));
        } finally {
            unavailableFactory.destroy();
        }
    }

    private static AsyncJobWorker worker(
            AsyncJobStore store, WebhookProcessingHandler handler, AsyncJobProperties properties) {
        return new AsyncJobWorker(redis, store, objectMapper, handler, properties);
    }

    private static AsyncJobStore realStore(AsyncJobProperties properties) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgresContainer.getJdbcUrl(),
                postgresContainer.getUsername(),
                postgresContainer.getPassword());
        return new AsyncJobStore(new JdbcTemplate(dataSource), objectMapper, properties);
    }

    private static AsyncJobStore mockStore(UUID jobId, int attempts, int maxAttempts, boolean claimed) {
        AsyncJobStore store = mock(AsyncJobStore.class);
        when(store.claimJob(jobId)).thenReturn(new AsyncJobStore.JobState(
                claimed ? AsyncJobStore.JobStatus.PROCESSING : AsyncJobStore.JobStatus.SUCCEEDED,
                attempts, maxAttempts, claimed));
        doNothing().when(store).recoverStaleJobs(any());
        return store;
    }

    private static AsyncJobProperties properties(
            String stream, String group, String consumer, int concurrency) {
        return properties(stream, group, consumer, concurrency, 30_000);
    }

    private static AsyncJobProperties properties(
            String stream, String group, String consumer, int concurrency, long pendingIdleMs) {
        AsyncJobProperties properties = new AsyncJobProperties();
        properties.getWorker().setStream(stream);
        properties.getWorker().setConsumerGroup(group);
        properties.getWorker().setConsumerName(consumer);
        properties.getWorker().setConcurrency(concurrency);
        properties.getWorker().setBlockTimeoutMs(50);
        properties.getWorker().setPendingIdleMs(pendingIdleMs);
        properties.getWorker().setJobTimeoutMs(5000);
        properties.getWorker().setShutdownTimeoutMs(5000);
        return properties;
    }

    private static UUID add(String stream, JobEnvelope envelope) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("schemaVersion", Integer.toString(envelope.schemaVersion()));
        fields.put("type", envelope.type().name());
        fields.put("jobId", envelope.jobId().toString());
        fields.put("eventId", envelope.eventId().toString());
        fields.put("attempt", Integer.toString(envelope.attempt()));
        fields.put("createdAt", envelope.createdAt().toString());
        return UUID.nameUUIDFromBytes(redis.opsForStream().add(
                StreamRecords.newRecord().in(stream).ofMap(fields)).getValue().getBytes());
    }

    private static int pending(String stream, String group) {
        try {
            return Math.toIntExact(redis.opsForStream().pending(stream, group).getTotalPendingMessages());
        } catch (RedisSystemException exception) {
            return -1;
        }
    }

    private static String stream() {
        return "test:jobs:" + UUID.randomUUID();
    }

    private static String group() {
        return "test-group-" + UUID.randomUUID();
    }

    private static AsyncJobStore.OutboxRecord outboxRecord(UUID jobId) throws Exception {
        UUID outboxId = UUID.randomUUID();
        JobEnvelope envelope = JobEnvelope.webhook(jobId, UUID.randomUUID(), 1);
        return new AsyncJobStore.OutboxRecord(
                outboxId, jobId, JobType.WEBHOOK_PROCESSING,
                objectMapper.writeValueAsString(envelope), 1);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
