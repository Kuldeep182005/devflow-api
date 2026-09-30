package com.devflow.api.task.api;

import com.devflow.api.organization.api.PageResponse;
import com.devflow.api.security.AuthenticatedUser;
import com.devflow.api.task.service.TaskService;
import com.devflow.api.task.service.TaskStateService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskStateService taskStateService;

    public TaskController(TaskService taskService, TaskStateService taskStateService) {
        this.taskService = taskService;
        this.taskStateService = taskStateService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TaskResponse.from(taskService.create(principal.id(), organizationId, request)));
    }

    @GetMapping
    public PageResponse<TaskResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @RequestParam(required = false) UUID repositoryId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return page(taskService.list(principal.id(), organizationId, repositoryId, pageable).map(TaskResponse::from));
    }

    @GetMapping("/{taskId}")
    public TaskResponse get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PathVariable UUID taskId) {
        return TaskResponse.from(taskService.get(principal.id(), organizationId, taskId));
    }

    @PostMapping("/{taskId}/cancel")
    public TaskResponse cancel(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PathVariable UUID taskId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return TaskResponse.from(taskStateService.cancelTask(
                principal.id(), organizationId, taskId, idempotencyKey));
    }

    @GetMapping("/{taskId}/events")
    public PageResponse<TaskEventResponse> events(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PathVariable UUID taskId,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return page(taskService.events(principal.id(), organizationId, taskId, pageable)
                .map(TaskEventResponse::from));
    }

    private static <T> PageResponse<T> page(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
