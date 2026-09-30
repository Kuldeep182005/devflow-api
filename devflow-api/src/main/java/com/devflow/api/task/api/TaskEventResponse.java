package com.devflow.api.task.api;

import com.devflow.api.task.domain.TaskActorSource;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.task.domain.TaskEventType;
import com.devflow.api.task.domain.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record TaskEventResponse(
        UUID id,
        UUID taskId,
        UUID organizationId,
        TaskEventType eventType,
        TaskStatus previousState,
        TaskStatus newState,
        TaskActorSource actorSource,
        UUID actorUserId,
        Instant occurredAt,
        String correlationId) {

    public static TaskEventResponse from(TaskEvent event) {
        return new TaskEventResponse(
                event.getId(),
                event.getTask().getId(),
                event.getOrganization().getId(),
                event.getEventType(),
                event.getPreviousState(),
                event.getNewState(),
                event.getActorSource(),
                event.getActorUserId(),
                event.getOccurredAt(),
                event.getCorrelationId());
    }
}
