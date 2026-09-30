package com.devflow.api.task.api;

import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.security.AuthenticatedUser;
import com.devflow.api.security.JwtService;
import com.devflow.api.task.service.TaskService;
import com.devflow.api.task.service.TaskStateService;
import com.devflow.api.task.domain.Task;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.user.domain.User;
import com.devflow.api.web.ApiException;
import com.devflow.api.web.GlobalExceptionHandler;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import com.devflow.api.security.SecurityConfig;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TaskControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @MockBean
    private TaskStateService taskStateService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void unauthenticatedUserCannotCancelOrReadEvents() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/tasks/{taskId}/cancel",
                        organizationId, taskId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/tasks/{taskId}/events",
                        organizationId, taskId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedMemberCanCancelTask() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "member@example.com");
        Task task = org.mockito.Mockito.mock(Task.class);
        Organization organization = org.mockito.Mockito.mock(Organization.class);
        Repository repository = org.mockito.Mockito.mock(Repository.class);
        User createdBy = org.mockito.Mockito.mock(User.class);
        when(task.getOrganization()).thenReturn(organization);
        when(task.getRepository()).thenReturn(repository);
        when(task.getCreatedBy()).thenReturn(createdBy);
        when(organization.getId()).thenReturn(organizationId);
        when(repository.getId()).thenReturn(UUID.randomUUID());
        when(createdBy.getId()).thenReturn(user.id());
        when(taskStateService.cancelTask(user.id(), organizationId, taskId, "cancel-1")).thenReturn(task);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/tasks/{taskId}/cancel",
                        organizationId, taskId)
                        .header("Idempotency-Key", "cancel-1")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities()))))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserReceivesSafeNotFoundForCrossOrganizationTask() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "member@example.com");
        when(taskService.get(user.id(), organizationId, taskId))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND",
                        "The task does not exist"));

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/tasks/{taskId}",
                        organizationId, taskId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void viewerCancellationIsForbidden() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "viewer@example.com");
        when(taskStateService.cancelTask(user.id(), organizationId, taskId, null))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "TASK_CANCEL_FORBIDDEN",
                        "You do not have permission to cancel tasks"));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/tasks/{taskId}/cancel",
                        organizationId, taskId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TASK_CANCEL_FORBIDDEN"));
    }

    @Test
    void terminalTaskCancellationReturnsConflict() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "member@example.com");
        when(taskStateService.cancelTask(user.id(), organizationId, taskId, null))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "INVALID_TASK_TRANSITION",
                        "The task cannot transition from SUCCEEDED to CANCELLED"));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/tasks/{taskId}/cancel",
                        organizationId, taskId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TASK_TRANSITION"));
    }

    @Test
    void malformedTaskUuidReturnsBadRequest() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "member@example.com");
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/tasks/not-a-uuid",
                        UUID.randomUUID())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eventMutationEndpointsDoNotExist() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "member@example.com");
        mockMvc.perform(post("/api/v1/organizations/{organizationId}/tasks/{taskId}/events",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities()))))
                        .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void eventsEndpointReturnsStableEventShapeAndPaginationMetadata() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "member@example.com");
        Task task = org.mockito.Mockito.mock(Task.class);
        Organization organization = org.mockito.Mockito.mock(Organization.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getOrganization()).thenReturn(organization);
        when(organization.getId()).thenReturn(organizationId);
        com.devflow.api.task.domain.TaskEvent event = org.mockito.Mockito.mock(
                        com.devflow.api.task.domain.TaskEvent.class);
        when(event.getId()).thenReturn(UUID.randomUUID());
        when(event.getTask()).thenReturn(task);
        when(event.getOrganization()).thenReturn(organization);
        when(event.getEventType()).thenReturn(
                        com.devflow.api.task.domain.TaskEventType.TASK_STARTED);
        when(event.getPreviousState()).thenReturn(
                        com.devflow.api.task.domain.TaskStatus.QUEUED);
        when(event.getNewState()).thenReturn(
                        com.devflow.api.task.domain.TaskStatus.RUNNING);
        when(event.getActorSource()).thenReturn(
                        com.devflow.api.task.domain.TaskActorSource.WORKER);
        when(event.getOccurredAt()).thenReturn(Instant.now());
        when(taskService.events(eq(user.id()), eq(organizationId), eq(taskId), any()))
                        .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(
                                0, 1, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))), 2));

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/tasks/{taskId}/events",
                                organizationId, taskId)
                                .param("page", "0")
                                .param("size", "1")
                                .with(authentication(new UsernamePasswordAuthenticationToken(
                                        user, null, user.getAuthorities()))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content[0].id").isNotEmpty())
                        .andExpect(jsonPath("$.content[0].taskId").value(taskId.toString()))
                        .andExpect(jsonPath("$.content[0].organizationId").value(organizationId.toString()))
                        .andExpect(jsonPath("$.content[0].eventType").value("TASK_STARTED"))
                        .andExpect(jsonPath("$.content[0].previousState").value("QUEUED"))
                        .andExpect(jsonPath("$.content[0].newState").value("RUNNING"))
                        .andExpect(jsonPath("$.content[0].actorSource").value("WORKER"))
                        .andExpect(jsonPath("$.content[0].actorUserId").doesNotExist())
                        .andExpect(jsonPath("$.content[0].occurredAt").isNotEmpty())
                        .andExpect(jsonPath("$.totalElements").value(2))
                        .andExpect(jsonPath("$.size").value(1));
    }

}
