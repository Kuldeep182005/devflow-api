package com.devflow.api.task.service;

import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.repository.RepositoryRepository;
import com.devflow.api.persistence.task.TaskRepository;
import com.devflow.api.persistence.task.TaskEventRepository;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.task.domain.TaskEventType;
import com.devflow.api.task.domain.TaskActor;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.task.api.CreateTaskRequest;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskSourceType;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.web.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final RepositoryRepository repositoryRepository;
    private final OrganizationMemberRepository memberRepository;
    private final TaskEventRepository eventRepository;

    public TaskService(
            TaskRepository taskRepository,
            RepositoryRepository repositoryRepository,
            OrganizationMemberRepository memberRepository,
            TaskEventRepository eventRepository) {
        this.taskRepository = taskRepository;
        this.repositoryRepository = repositoryRepository;
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public Task create(UUID principalId, UUID organizationId, CreateTaskRequest request) {
        OrganizationMember member = requireMembership(principalId, organizationId);
        Repository repository = repositoryRepository.findByIdAndOrganizationId(request.repositoryId(), organizationId)
                .orElseThrow(() -> notFound("REPOSITORY_NOT_FOUND", "The repository does not exist"));
        Instant now = Instant.now();
        Task task = new Task(
                member.getOrganization(),
                repository,
                member.getUser(),
                TaskSourceType.MANUAL,
                null,
                request.title().trim(),
                request.description(),
                TaskStatus.QUEUED,
                null,
                null,
                null,
                now,
                now,
                null,
                null);
        task = taskRepository.saveAndFlush(task);
        eventRepository.saveAndFlush(new TaskEvent(
                task,
                task.getOrganization(),
                TaskEventType.TASK_CREATED,
                null,
                TaskStatus.QUEUED,
                TaskActor.user(principalId),
                now,
                null,
                "task-created:" + task.getId()));
        return task;
    }

    @Transactional(readOnly = true)
    public Page<Task> list(
            UUID principalId, UUID organizationId, UUID repositoryId, Pageable pageable) {
        requireMembership(principalId, organizationId);
        if (repositoryId != null
                && !repositoryRepository.existsByIdAndOrganizationId(repositoryId, organizationId)) {
            throw notFound("REPOSITORY_NOT_FOUND", "The repository does not exist");
        }
        return repositoryId == null
                ? taskRepository.findAllByOrganizationId(organizationId, boundedPageable(pageable))
                : taskRepository.findAllByOrganizationIdAndRepositoryId(
                        organizationId, repositoryId, boundedPageable(pageable));
    }

    @Transactional(readOnly = true)
    public Task get(UUID principalId, UUID organizationId, UUID taskId) {
        requireMembership(principalId, organizationId);
        return taskRepository.findByIdAndOrganizationId(taskId, organizationId)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "The task does not exist"));
    }

    @Transactional(readOnly = true)
    public Page<com.devflow.api.task.domain.TaskEvent> events(
            UUID principalId, UUID organizationId, UUID taskId, Pageable pageable) {
        requireMembership(principalId, organizationId);
        if (taskRepository.findByIdAndOrganizationId(taskId, organizationId).isEmpty()) {
            throw notFound("TASK_NOT_FOUND", "The task does not exist");
        }
        Pageable bounded = boundedPageable(pageable);
        Pageable ordered = org.springframework.data.domain.PageRequest.of(
                bounded.getPageNumber(),
                bounded.getPageSize(),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("occurredAt"),
                        org.springframework.data.domain.Sort.Order.desc("id")));
        return eventRepository.findAllByTaskIdAndOrganizationId(taskId, organizationId, ordered);
    }

    private OrganizationMember requireMembership(UUID principalId, UUID organizationId) {
        if (principalId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
        }
        return memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, principalId)
                .orElseThrow(() -> notFound("ORGANIZATION_NOT_FOUND", "The organization does not exist"));
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private Pageable boundedPageable(Pageable pageable) {
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 100),
                pageable.getSort());
    }
}
