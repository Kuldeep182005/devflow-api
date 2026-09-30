package com.devflow.api.async.worker;

import com.devflow.api.async.config.AsyncJobProperties;
import com.devflow.api.async.job.JobEnvelope;
import com.devflow.api.async.persistence.AsyncJobStore;
import com.devflow.api.observability.metrics.DevFlowMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.context.SmartLifecycle;

@Component
@ConditionalOnProperty(name = "async.worker.enabled", havingValue = "true", matchIfMissing = true)
public class AsyncJobWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AsyncJobWorker.class);
    private final StringRedisTemplate redis;
    private final AsyncJobStore store;
    private final ObjectMapper objectMapper;
    private final WebhookProcessingHandler handler;
    private final AsyncJobProperties properties;
    private final DevFlowMetrics metrics;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Semaphore permits;
    private final ExecutorService dispatchExecutor;
    private final ExecutorService handlerExecutor;
    private Thread consumerThread;

    public AsyncJobWorker(
            StringRedisTemplate redis,
            AsyncJobStore store,
            ObjectMapper objectMapper,
            WebhookProcessingHandler handler,
            AsyncJobProperties properties) {
        this(redis, store, objectMapper, handler, properties, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AsyncJobWorker(
            StringRedisTemplate redis,
            AsyncJobStore store,
            ObjectMapper objectMapper,
            WebhookProcessingHandler handler,
            AsyncJobProperties properties,
            DevFlowMetrics metrics) {
        this.redis = redis;
        this.store = store;
        this.objectMapper = objectMapper;
        this.handler = handler;
        this.properties = properties;
        this.metrics = metrics;
        int concurrency = Math.max(1, properties.getWorker().getConcurrency());
        this.permits = new Semaphore(concurrency);
        this.dispatchExecutor = Executors.newFixedThreadPool(concurrency, runnable -> {
            Thread thread = new Thread(runnable, "async-job-dispatch");
            thread.setDaemon(true);
            return thread;
        });
        this.handlerExecutor = Executors.newFixedThreadPool(concurrency, runnable -> {
            Thread thread = new Thread(runnable, "async-job-handler");
            thread.setDaemon(true);
            return thread;
        });
    }


    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            consumerThread = new Thread(this::consume, "async-job-consumer");
            consumerThread.setDaemon(true);
            consumerThread.start();
        }
    }

    private void consume() {
        while (running.get()) {
            try {
                store.recoverStaleJobs(Duration.ofMillis(Math.max(
                        properties.getWorker().getPendingIdleMs(),
                        properties.getWorker().getJobTimeoutMs())));
                StreamOperations<String, String, String> streams =
                        redis.opsForStream();
                ensureGroup(streams);
                int availablePermits = permits.availablePermits();
                if (availablePermits <= 0) {
                    Thread.sleep(25);
                    continue;
                }
                int count = Math.min(availablePermits,
                        Math.max(1, properties.getWorker().getBatchSize()));
                recoverPending(streams, count);
                List<MapRecord<String, String, String>> records = streams.read(
                        Consumer.from(properties.getWorker().getConsumerGroup(),
                                properties.getWorker().getConsumerName()),
                        StreamReadOptions.empty()
                                .count(count)
                                .block(Duration.ofMillis(Math.max(100, properties.getWorker().getBlockTimeoutMs()))),
                        StreamOffset.create(properties.getWorker().getStream(), ReadOffset.lastConsumed()));
                if (records != null) {
                    records.forEach(this::dispatch);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                log.debug("Async worker Redis unavailable: {}", exception.getClass().getSimpleName());
                try {
                    Thread.sleep(250);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void ensureGroup(StreamOperations<String, String, String> streams) {
        try {
            streams.createGroup(properties.getWorker().getStream(), ReadOffset.from("0-0"),
                    properties.getWorker().getConsumerGroup());
        } catch (RedisSystemException exception) {
            if (!isBusyGroup(exception)) {
                throw exception;
            }
        }
    }

    private boolean isBusyGroup(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (String.valueOf(current.getMessage()).toUpperCase().contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void recoverPending(StreamOperations<String, String, String> streams, int count) {
        PendingMessages pending = streams.pending(
                properties.getWorker().getStream(),
                properties.getWorker().getConsumerGroup(),
                Range.unbounded(),
                count);
        if (pending == null) {
            return;
        }
        for (PendingMessage message : pending) {
            if (!running.get() || message.getElapsedTimeSinceLastDelivery().toMillis() <
                    properties.getWorker().getPendingIdleMs()) {
                continue;
            }
            List<MapRecord<String, String, String>> claimed = streams.claim(
                    properties.getWorker().getStream(),
                    properties.getWorker().getConsumerGroup(),
                    properties.getWorker().getConsumerName(),
                    Duration.ofMillis(Math.max(1, properties.getWorker().getPendingIdleMs())),
                    message.getId());
            if (claimed != null) {
                if (metrics != null) {
                    metrics.recordStaleClaimRecovered();
                }
                claimed.forEach(this::dispatch);
            }
        }
    }

    private void dispatch(MapRecord<String, String, String> record) {
            if (!permits.tryAcquire()) {
                // The record remains pending in Redis and will be recovered on a later pass.
                return;
            }
            try {
                dispatchExecutor.execute(() -> process(record));
            } catch (RuntimeException exception) {
                permits.release();
                log.debug("Async worker dispatch unavailable: {}", exception.getClass().getSimpleName());
            }
    }

    private void process(MapRecord<String, String, String> record) {
        JobEnvelope envelope;
        try {
            envelope = parse(record.getValue());
        } catch (Exception exception) {
            log.warn("Discarding malformed async job message: {}", exception.getClass().getSimpleName());
            if (metrics != null) {
                metrics.recordJobFailed(null, "MALFORMED_JOB", null);
            }
            ack(record);
            permits.release();
            return;
        }
        if (metrics != null) {
            metrics.recordJobReceived(envelope.type());
        }
        Instant startTime = Instant.now();
        try {
            AsyncJobStore.JobState state = store.claimJob(envelope.jobId());
            if (!state.claimed()) {
                ack(record);
                permits.release();
                return;
            }
            Future<?> handlerFuture = handlerExecutor.submit(() -> {
                String correlationId = "job-" + envelope.jobId();
                org.slf4j.MDC.put("requestId", correlationId);
                try {
                    handler.process(envelope);
                } catch (Exception exception) {
                    throw new HandlerFailure(exception);
                } finally {
                    org.slf4j.MDC.remove("requestId");
                }
            });
            try {
                handlerFuture.get(Math.max(1, properties.getWorker().getJobTimeoutMs()), TimeUnit.MILLISECONDS);
                store.markSucceeded(envelope.jobId());
                if (metrics != null) {
                    metrics.recordJobSucceeded(envelope.type(), Duration.between(startTime, Instant.now()));
                }
            } catch (TimeoutException exception) {
                handlerFuture.cancel(true);
                fail(envelope, state, exception);
                if (metrics != null) {
                    metrics.recordJobFailed(envelope.type(), "TIMEOUT", Duration.between(startTime, Instant.now()));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                handlerFuture.cancel(true);
                fail(envelope, state, exception);
                if (metrics != null) {
                    metrics.recordJobFailed(envelope.type(), "INTERRUPTED", Duration.between(startTime, Instant.now()));
                }
            } catch (ExecutionException exception) {
                fail(envelope, state, exception.getCause());
                if (metrics != null) {
                    metrics.recordJobFailed(envelope.type(), "EXECUTION_ERROR", Duration.between(startTime, Instant.now()));
                }
            }
            ack(record);
        } catch (Exception exception) {
            // Preserve the pending Redis entry when durable state cannot be updated.
            log.debug("Async job state unavailable: {}", exception.getClass().getSimpleName());
        } finally {
            permits.release();
        }
    }

    private JobEnvelope parse(Map<String, String> fields) throws Exception {
        return objectMapper.readValue(objectMapper.writeValueAsBytes(fields), JobEnvelope.class);
    }

    private void fail(JobEnvelope envelope, AsyncJobStore.JobState state, Throwable failure) {
        long base = Math.max(1, properties.getWorker().getRetryBaseDelayMs());
        long delay = Math.min(base * (1L << Math.min(10, Math.max(0, state.attempts() - 1))),
                TimeUnit.MINUTES.toMillis(5));
        store.markFailedOrRetry(envelope.jobId(), failure == null ? "job failed" : failure.getClass().getSimpleName(),
                state.attempts(), state.maxAttempts(), delay);
        if (metrics != null && state.attempts() < state.maxAttempts()) {
            metrics.recordJobRetried(envelope.type());
        }
    }


    private void ack(MapRecord<String, String, String> record) {
        try {
            redis.opsForStream().acknowledge(
                    properties.getWorker().getStream(),
                    properties.getWorker().getConsumerGroup(),
                    record.getId());
        } catch (Exception exception) {
            log.debug("Async worker acknowledgement unavailable: {}", exception.getClass().getSimpleName());
        }
    }

    @Override
    public void stop(Runnable callback) {
        running.set(false);
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        dispatchExecutor.shutdown();
        long timeout = Math.max(1, properties.getWorker().getShutdownTimeoutMs());
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        try {
            if (consumerThread != null) {
                long remaining = deadline - System.nanoTime();
                if (remaining > 0) {
                    consumerThread.join(TimeUnit.NANOSECONDS.toMillis(remaining));
                }
            }
            long remaining = deadline - System.nanoTime();
            if (remaining > 0 && !dispatchExecutor.awaitTermination(
                    TimeUnit.NANOSECONDS.toMillis(remaining), TimeUnit.MILLISECONDS)) {
                dispatchExecutor.shutdownNow();
            }
            handlerExecutor.shutdown();
            remaining = deadline - System.nanoTime();
            if (remaining > 0 && !handlerExecutor.awaitTermination(
                    TimeUnit.NANOSECONDS.toMillis(remaining), TimeUnit.MILLISECONDS)) {
                handlerExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            dispatchExecutor.shutdownNow();
            handlerExecutor.shutdownNow();
        }
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void stop() {
        stop(() -> { });
    }

    private static final class HandlerFailure extends RuntimeException {
        private HandlerFailure(Exception cause) {
            super(cause);
        }
    }
}
