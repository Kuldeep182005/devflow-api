package com.devflow.api.async.outbox;

import com.devflow.api.async.config.AsyncJobProperties;
import com.devflow.api.async.job.JobEnvelope;
import com.devflow.api.async.persistence.AsyncJobStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AsyncJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(AsyncJobPublisher.class);
    private final AsyncJobStore store;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AsyncJobProperties properties;

    public AsyncJobPublisher(
            AsyncJobStore store,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            AsyncJobProperties properties) {
        this.store = store;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${async.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        List<AsyncJobStore.OutboxRecord> records;
        try {
            records = store.claimOutbox(properties.getOutbox().getBatchSize());
        } catch (Exception exception) {
            log.debug("Async outbox claim unavailable: {}", exception.getClass().getSimpleName());
            return;
        }
        for (AsyncJobStore.OutboxRecord record : records) {
            publish(record);
        }
    }

    private void publish(AsyncJobStore.OutboxRecord record) {
        JobEnvelope envelope;
        try {
            envelope = objectMapper.readValue(record.payload(), JobEnvelope.class);
        } catch (JsonProcessingException exception) {
            store.markOutboxFailed(record.outboxId(), "Invalid async job envelope");
            log.warn("Marking invalid async outbox row failed");
            return;
        }
        try {
            if (envelope.type() != record.type() || !envelope.jobId().equals(record.jobId())) {
                store.markOutboxFailed(record.outboxId(), "Envelope does not match outbox row");
                return;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("schemaVersion", Integer.toString(envelope.schemaVersion()));
            fields.put("type", envelope.type().name());
            fields.put("jobId", envelope.jobId().toString());
            fields.put("eventId", envelope.eventId().toString());
            fields.put("attempt", Integer.toString(envelope.attempt()));
            fields.put("createdAt", envelope.createdAt().toString());
            MapRecord<String, String, String> message = StreamRecords.newRecord()
                    .in(properties.getWorker().getStream())
                    .ofMap(fields);
            RecordId recordId = redis.opsForStream().add(message);
            if (recordId == null) {
                throw new IllegalStateException("Redis did not return a stream id");
            }
            store.markPublished(record.outboxId());
        } catch (Exception exception) {
            store.releaseOutbox(record.outboxId(), exception.getClass().getSimpleName(),
                    properties.getOutbox().getRetryDelayMs());
            log.debug("Async outbox publish unavailable: {}", exception.getClass().getSimpleName());
        }
    }
}
