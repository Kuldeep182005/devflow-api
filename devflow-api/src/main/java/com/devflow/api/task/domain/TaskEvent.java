package com.devflow.api.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
import com.devflow.api.organization.domain.Organization;

@Entity
@Table(name = "task_events")
public class TaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private TaskEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state", length = 30)
    private TaskStatus previousState;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_state", nullable = false, length = 30)
    private TaskStatus newState;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_source", nullable = false, length = 20)
    private TaskActorSource actorSource;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    protected TaskEvent() {
    }

    public TaskEvent(
            Task task,
            Organization organization,
            TaskEventType eventType,
            TaskStatus previousState,
            TaskStatus newState,
            TaskActor actor,
            Instant occurredAt,
            String correlationId,
            String idempotencyKey) {
        this.task = task;
        this.organization = organization;
        this.eventType = eventType;
        this.previousState = previousState;
        this.newState = newState;
        this.actorSource = actor.source();
        this.actorUserId = actor.userId();
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.idempotencyKey = idempotencyKey;
    }

    public TaskEvent(
            Task task,
            Organization organization,
            TaskEventType eventType,
            TaskStatus previousState,
            TaskStatus newState,
            TaskActor actor,
            Instant occurredAt,
            String correlationId,
            String idempotencyKey,
            String metadata) {
        this(task, organization, eventType, previousState, newState, actor, occurredAt, correlationId, idempotencyKey);
        this.metadata = metadata;
    }

    public UUID getId() { return id; }
    public Task getTask() { return task; }
    public Organization getOrganization() { return organization; }
    public TaskEventType getEventType() { return eventType; }
    public TaskStatus getPreviousState() { return previousState; }
    public TaskStatus getNewState() { return newState; }
    public TaskActorSource getActorSource() { return actorSource; }
    public UUID getActorUserId() { return actorUserId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getMetadata() { return metadata; }
}
