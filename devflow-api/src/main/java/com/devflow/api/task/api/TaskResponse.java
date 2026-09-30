package com.devflow.api.task.api;

import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskSourceType;
import com.devflow.api.task.domain.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID organizationId,
        UUID repositoryId,
        UUID createdByUserId,
        TaskSourceType sourceType,
        String sourceReference,
        String title,
        String description,
        TaskStatus status,
        String branchName,
        String currentStep,
        String failureCode,
        String failureMessage,
        Instant failedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant finishedAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getOrganization().getId(),
                task.getRepository().getId(),
                task.getCreatedBy().getId(),
                task.getSourceType(),
                task.getSourceReference(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getBranchName(),
                task.getCurrentStep(),
                task.getFailureCode(),
                task.getFailureMessage(),
                task.getFailedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getStartedAt(),
                task.getFinishedAt());
    }
}
