package com.devflow.api.task.service;

import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.repository.RepositoryRepository;
import com.devflow.api.persistence.task.TaskRepository;
import com.devflow.api.persistence.task.TaskEventRepository;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.task.api.CreateTaskRequest;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.web.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private TaskEventRepository eventRepository;

    @Mock
    private OrganizationMember member;

    @Mock
    private Repository repository;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, repositoryRepository, memberRepository, eventRepository);
    }

    @Test
    void rejectsTaskForRepositoryFromAnotherOrganization() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));
        when(repositoryRepository.findByIdAndOrganizationId(repositoryId, organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(userId, organizationId,
                new CreateTaskRequest(repositoryId, "Build API", null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(404);
                    assertThat(exception.getCode()).isEqualTo("REPOSITORY_NOT_FOUND");
                });
    }

    @Test
    void alwaysCreatesQueuedManualTask() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));
        when(repositoryRepository.findByIdAndOrganizationId(repositoryId, organizationId))
                .thenReturn(Optional.of(repository));
        when(taskRepository.saveAndFlush(any(com.devflow.api.task.domain.Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var task = service.create(userId, organizationId,
                new CreateTaskRequest(repositoryId, " Build API ", "description"));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(task.getTitle()).isEqualTo("Build API");
        assertThat(task.getBranchName()).isNull();
        ArgumentCaptor<TaskEvent> event = ArgumentCaptor.forClass(TaskEvent.class);
        verify(eventRepository).saveAndFlush(event.capture());
        assertThat(event.getValue().getTask()).isSameAs(task);
        assertThat(event.getValue().getEventType()).isEqualTo(
                com.devflow.api.task.domain.TaskEventType.TASK_CREATED);
        assertThat(event.getValue().getPreviousState()).isNull();
        assertThat(event.getValue().getNewState()).isEqualTo(TaskStatus.QUEUED);
        assertThat(event.getValue().getActorSource()).isEqualTo(
                com.devflow.api.task.domain.TaskActorSource.USER);
        assertThat(event.getValue().getActorUserId()).isEqualTo(userId);
        assertThat(event.getValue().getOccurredAt()).isNotNull();
    }

    @Test
    void returnsNotFoundForTaskOutsideOrganization() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));
        when(taskRepository.findByIdAndOrganizationId(taskId, organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId, organizationId, taskId))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("TASK_NOT_FOUND"));
    }
}
