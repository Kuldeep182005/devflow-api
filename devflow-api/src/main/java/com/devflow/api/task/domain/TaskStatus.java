package com.devflow.api.task.domain;

public enum TaskStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_APPROVAL,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
