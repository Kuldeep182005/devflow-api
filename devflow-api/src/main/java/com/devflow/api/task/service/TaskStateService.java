package com.devflow.api.task.service;

import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.task.TaskEventRepository;
import com.devflow.api.persistence.task.TaskRepository;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskActor;
import com.devflow.api.task.domain.TaskActorSource;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.task.domain.TaskEventType;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.web.ApiException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskStateService {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = Map.of(
            TaskStatus.QUEUED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED),
            TaskStatus.RUNNING, EnumSet.of(
                    TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.SUCCEEDED,
                    TaskStatus.FAILED, TaskStatus.CANCELLED),
            TaskStatus.WAITING_FOR_APPROVAL, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED),
            TaskStatus.SUCCEEDED, Set.of(),
            TaskStatus.FAILED, Set.of(),
            TaskStatus.CANCELLED, Set.of());

    private final TaskRepository taskRepository;
    private final TaskEventRepository eventRepository;
    private final OrganizationMemberRepository memberRepository;

    public TaskStateService(
            TaskRepository taskRepository,
            TaskEventRepository eventRepository,
            OrganizationMemberRepository memberRepository) {
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Task startTask(UUID organizationId, UUID taskId, TaskActor actor, String idempotencyKey) {
        return transition(organizationId, taskId, TaskStatus.RUNNING, TaskEventType.TASK_STARTED,
                actor, idempotencyKey, null, null);
    }

    @Transactional
    public Task requestApproval(UUID organizationId, UUID taskId, TaskActor actor, String idempotencyKey) {
        return transition(organizationId, taskId, TaskStatus.WAITING_FOR_APPROVAL,
                TaskEventType.TASK_APPROVAL_REQUIRED, actor, idempotencyKey, null, null);
    }

    @Transactional
    public Task resumeTask(UUID organizationId, UUID taskId, TaskActor actor, String idempotencyKey) {
        return transition(organizationId, taskId, TaskStatus.RUNNING, TaskEventType.TASK_RESUMED,
                actor, idempotencyKey, null, null);
    }

    @Transactional
    public Task succeedTask(UUID organizationId, UUID taskId, TaskActor actor, String idempotencyKey) {
        return transition(organizationId, taskId, TaskStatus.SUCCEEDED, TaskEventType.TASK_SUCCEEDED,
                actor, idempotencyKey, null, null);
    }

    @Transactional
    public Task failTask(
            UUID organizationId,
            UUID taskId,
            TaskActor actor,
            String idempotencyKey,
            String failureCode,
            String failureMessage) {
        validateFailure(failureCode, failureMessage);
        return transition(organizationId, taskId, TaskStatus.FAILED, TaskEventType.TASK_FAILED,
                actor, idempotencyKey, failureCode, failureMessage);
    }

    @Transactional
    public Task cancelTask(UUID principalId, UUID organizationId, UUID taskId, String idempotencyKey) {
        OrganizationMember member = requireMembership(principalId, organizationId);
        if (member.getRole() == OrganizationRole.VIEWER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_CANCEL_FORBIDDEN",
                    "You do not have permission to cancel tasks");
        }
        return transition(organizationId, taskId, TaskStatus.CANCELLED,
                TaskEventType.TASK_CANCELLED, TaskActor.user(principalId),
                idempotencyKey, null, null);
    }

    private Task transition(
            UUID organizationId,
            UUID taskId,
            TaskStatus next,
            TaskEventType eventType,
            TaskActor actor,
            String idempotencyKey,
            String failureCode,
            String failureMessage) {
        validateActor(actor);
        validateIdempotencyKey(idempotencyKey);
        Task task = taskRepository.findByIdAndOrganizationId(taskId, organizationId)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "The task does not exist"));
        if (idempotencyKey != null) {
            var existing = eventRepository.findByTaskIdAndIdempotencyKey(taskId, idempotencyKey);
            if (existing.isPresent()) {
                if (existing.get().getEventType() != eventType
                        || existing.get().getNewState() != next) {
                    throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                            "The idempotency key was used for a different transition");
                }
                return existing.get().getTask();
            }
        }
        if (actor.source() == TaskActorSource.USER && eventType != TaskEventType.TASK_CANCELLED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_TRANSITION_FORBIDDEN",
                    "This transition is not available to user requests");
        }
        TaskStatus previous = task.getStatus();
        if (!ALLOWED.get(previous).contains(next)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TASK_TRANSITION",
                    "The task cannot transition from " + previous + " to " + next);
        }
        Instant now = Instant.now();
        task.transitionTo(next, now);
        if (next == TaskStatus.FAILED) {
            task.recordFailure(failureCode, failureMessage, now);
        }
        try {
            taskRepository.saveAndFlush(task);
            eventRepository.saveAndFlush(new TaskEvent(
                    task, task.getOrganization(), eventType, previous, next, actor, now, null, idempotencyKey));
            return task;
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "TASK_TRANSITION_CONFLICT",
                    "The task was changed by another request");
        }
    }

    private OrganizationMember requireMembership(UUID principalId, UUID organizationId) {
        if (principalId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
        }
        return memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, principalId)
                .orElseThrow(() -> notFound("ORGANIZATION_NOT_FOUND", "The organization does not exist"));
    }

    private void validateActor(TaskActor actor) {
        if (actor == null || actor.source() == null
                || (actor.source() == TaskActorSource.USER) != (actor.userId() != null)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                    "Invalid task transition source");
        }
    }

    private void validateFailure(String code, String message) {
        if (!Set.of("WORKER_TIMEOUT", "INVALID_TASK", "EXTERNAL_SERVICE_FAILURE",
                "EXECUTION_FAILED", "INTERNAL_ERROR").contains(code)
                || message == null || message.isBlank() || message.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FAILURE",
                    "The failure information is invalid");
        }
    }

    private void validateIdempotencyKey(String key) {
        if (key != null && (key.isBlank() || key.length() > 255)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                "The idempotency key must contain 1 to 255 characters");
        }
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
