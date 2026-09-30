package com.devflow.api.task.domain;

import java.util.UUID;

public record TaskActor(TaskActorSource source, UUID userId) {

    public TaskActor {
        if (source == null || (source == TaskActorSource.USER) != (userId != null)) {
            throw new IllegalArgumentException("Invalid task actor");
        }
    }

    public static TaskActor user(UUID userId) {
        return new TaskActor(TaskActorSource.USER, userId);
    }

    public static TaskActor worker() {
        return new TaskActor(TaskActorSource.WORKER, null);
    }

    public static TaskActor system() {
        return new TaskActor(TaskActorSource.SYSTEM, null);
    }
}
