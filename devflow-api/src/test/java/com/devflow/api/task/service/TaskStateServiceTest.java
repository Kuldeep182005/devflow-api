package com.devflow.api.task.service;

import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.task.TaskEventRepository;
import com.devflow.api.persistence.task.TaskRepository;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskActor;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.task.domain.TaskEventType;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.web.ApiException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskStateServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskEventRepository eventRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private Task task;

    @Mock
    private OrganizationMember member;

    private TaskStateService service;

    @BeforeEach
    void setUp() {
        service = new TaskStateService(taskRepository, eventRepository, memberRepository);
        org.mockito.Mockito.lenient().when(taskRepository.findByIdAndOrganizationId(TASK_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(task));
    }

    @ParameterizedTest
    @MethodSource("validTransitions")
    void persistsExactlyOneEventForEveryValidWorkerTransition(
            TaskStatus initial, TaskStatus expected, TaskEventType eventType) {
        doReturn(initial).when(task).getStatus();

        invoke(expected, eventType);

        verify(taskRepository).saveAndFlush(task);
        ArgumentCaptor<TaskEvent> captor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(eventRepository).saveAndFlush(captor.capture());
        TaskEvent event = captor.getValue();
        assertThat(event.getTask()).isSameAs(task);
        assertThat(event.getEventType()).isEqualTo(eventType);
        assertThat(event.getPreviousState()).isEqualTo(initial);
        assertThat(event.getNewState()).isEqualTo(expected);
        if (eventType == TaskEventType.TASK_CANCELLED) {
            assertThat(event.getActorSource().name()).isEqualTo("USER");
            assertThat(event.getActorUserId()).isEqualTo(USER_ID);
        } else {
            assertThat(event.getActorSource().name()).isEqualTo("WORKER");
            assertThat(event.getActorUserId()).isNull();
        }
        assertThat(event.getOccurredAt()).isNotNull();
    }

    static Stream<Arguments> validTransitions() {
        return Stream.of(
                Arguments.of(TaskStatus.QUEUED, TaskStatus.RUNNING, TaskEventType.TASK_STARTED),
                Arguments.of(TaskStatus.QUEUED, TaskStatus.CANCELLED, TaskEventType.TASK_CANCELLED),
                Arguments.of(TaskStatus.RUNNING, TaskStatus.WAITING_FOR_APPROVAL,
                        TaskEventType.TASK_APPROVAL_REQUIRED),
                Arguments.of(TaskStatus.RUNNING, TaskStatus.SUCCEEDED, TaskEventType.TASK_SUCCEEDED),
                Arguments.of(TaskStatus.RUNNING, TaskStatus.FAILED, TaskEventType.TASK_FAILED),
                Arguments.of(TaskStatus.RUNNING, TaskStatus.CANCELLED, TaskEventType.TASK_CANCELLED),
                Arguments.of(TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.RUNNING, TaskEventType.TASK_RESUMED),
                Arguments.of(TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.CANCELLED,
                        TaskEventType.TASK_CANCELLED));
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void rejectsEveryInvalidTransitionWithConflict(TaskStatus initial, TaskStatus expected) {
        doReturn(initial).when(task).getStatus();

        assertThatThrownBy(() -> invoke(expected, eventTypeFor(expected)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                    assertThat(exception.getCode()).isEqualTo("INVALID_TASK_TRANSITION");
                });

        verify(taskRepository, never()).saveAndFlush(any());
        verify(eventRepository, never()).saveAndFlush(any());
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(TaskStatus.QUEUED, TaskStatus.SUCCEEDED),
                Arguments.of(TaskStatus.QUEUED, TaskStatus.FAILED),
                Arguments.of(TaskStatus.RUNNING, TaskStatus.QUEUED),
                Arguments.of(TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.SUCCEEDED),
                Arguments.of(TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.FAILED),
                Arguments.of(TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.QUEUED),
                Arguments.of(TaskStatus.SUCCEEDED, TaskStatus.RUNNING),
                Arguments.of(TaskStatus.SUCCEEDED, TaskStatus.CANCELLED),
                Arguments.of(TaskStatus.SUCCEEDED, TaskStatus.FAILED),
                Arguments.of(TaskStatus.FAILED, TaskStatus.RUNNING),
                Arguments.of(TaskStatus.FAILED, TaskStatus.CANCELLED),
                Arguments.of(TaskStatus.FAILED, TaskStatus.SUCCEEDED),
                Arguments.of(TaskStatus.CANCELLED, TaskStatus.RUNNING),
                Arguments.of(TaskStatus.CANCELLED, TaskStatus.SUCCEEDED),
                Arguments.of(TaskStatus.CANCELLED, TaskStatus.FAILED));
    }

    @Test
    void recordsFailureAndTerminalTimestamp() {
        when(task.getStatus()).thenReturn(TaskStatus.RUNNING);

        service.failTask(ORGANIZATION_ID, TASK_ID, TaskActor.worker(), "failure-1",
                "EXECUTION_FAILED", "execution failed");

        verify(task).recordFailure(eq("EXECUTION_FAILED"), eq("execution failed"), any(Instant.class));
        verify(task).transitionTo(eq(TaskStatus.FAILED), any(Instant.class));
    }

    @Test
    void maintainsLifecycleTimestampsAcrossTransitions() {
        Instant created = Instant.now().minusSeconds(10);
        Task realTask = new Task(null, null, null, null, null, "task", null,
                TaskStatus.QUEUED, null, null, null, created, created, null, null);
        when(taskRepository.findByIdAndOrganizationId(TASK_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(realTask));

        service.startTask(ORGANIZATION_ID, TASK_ID, TaskActor.worker(), null);
        Instant started = realTask.getStartedAt();
        assertThat(started).isNotNull();
        assertThat(realTask.getFinishedAt()).isNull();

        service.requestApproval(ORGANIZATION_ID, TASK_ID, TaskActor.worker(), null);
        assertThat(realTask.getFinishedAt()).isNull();

        service.resumeTask(ORGANIZATION_ID, TASK_ID, TaskActor.worker(), null);
        assertThat(realTask.getStartedAt()).isEqualTo(started);

        service.succeedTask(ORGANIZATION_ID, TASK_ID, TaskActor.worker(), null);
        assertThat(realTask.getFinishedAt()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("invalidFailureInformation")
    void rejectsInvalidFailureInformationWithoutMutatingTask(String code, String message) {
        assertThatThrownBy(() -> service.failTask(
                ORGANIZATION_ID, TASK_ID, TaskActor.worker(), null, code, message))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                    assertThat(exception.getCode()).isEqualTo("INVALID_FAILURE");
                });

        verify(task, never()).transitionTo(any(), any());
        verify(task, never()).recordFailure(any(), any(), any());
    }

    static Stream<Arguments> invalidFailureInformation() {
        return Stream.of(
                Arguments.of("UNKNOWN", "message"),
                Arguments.of("EXECUTION_FAILED", null),
                Arguments.of("EXECUTION_FAILED", ""),
                Arguments.of("EXECUTION_FAILED", " ".repeat(5)),
                Arguments.of("EXECUTION_FAILED", "x".repeat(2001)));
    }

    @Test
    void rejectsIdempotencyKeyReuseForDifferentTransition() {
        TaskEvent existing = new TaskEvent(task, null, TaskEventType.TASK_STARTED,
                TaskStatus.QUEUED, TaskStatus.RUNNING, TaskActor.worker(), Instant.now(), null, "same");
        when(eventRepository.findByTaskIdAndIdempotencyKey(TASK_ID, "same"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.succeedTask(
                ORGANIZATION_ID, TASK_ID, TaskActor.worker(), "same"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                    assertThat(exception.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                });
    }

    @Test
    void viewerCannotCancelTask() {
        when(memberRepository.findByIdOrganizationIdAndIdUserId(ORGANIZATION_ID, USER_ID))
                .thenReturn(Optional.of(member));
        when(member.getRole()).thenReturn(OrganizationRole.VIEWER);

        assertThatThrownBy(() -> service.cancelTask(USER_ID, ORGANIZATION_ID, TASK_ID, null))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(403);
                    assertThat(exception.getCode()).isEqualTo("TASK_CANCEL_FORBIDDEN");
                });
    }

    private void invoke(TaskStatus expected, TaskEventType eventType) {
        if (expected == TaskStatus.QUEUED) {
            try {
                Method transition = TaskStateService.class.getDeclaredMethod(
                        "transition", UUID.class, UUID.class, TaskStatus.class,
                        TaskEventType.class, TaskActor.class, String.class, String.class, String.class);
                transition.setAccessible(true);
                transition.invoke(service, ORGANIZATION_ID, TASK_ID, TaskStatus.QUEUED,
                        eventType, TaskActor.worker(), null, null, null);
                return;
            } catch (InvocationTargetException exception) {
                if (exception.getCause() instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(exception.getCause());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
        switch (eventType) {
            case TASK_STARTED -> service.startTask(ORGANIZATION_ID, TASK_ID, TaskActor.worker(), null);
            case TASK_APPROVAL_REQUIRED -> service.requestApproval(
                    ORGANIZATION_ID, TASK_ID, TaskActor.worker(), null);
            case TASK_RESUMED -> service.resumeTask(ORGANIZATION_ID, TASK_ID, TaskActor.worker(), null);
            case TASK_SUCCEEDED -> service.succeedTask(ORGANIZATION_ID, TASK_ID, TaskActor.worker(), null);
            case TASK_FAILED -> service.failTask(
                    ORGANIZATION_ID, TASK_ID, TaskActor.worker(), null,
                    "EXECUTION_FAILED", "execution failed");
            case TASK_CANCELLED -> {
                when(memberRepository.findByIdOrganizationIdAndIdUserId(ORGANIZATION_ID, USER_ID))
                        .thenReturn(Optional.of(member));
                when(member.getRole()).thenReturn(OrganizationRole.MEMBER);
                service.cancelTask(USER_ID, ORGANIZATION_ID, TASK_ID, null);
            }
            default -> throw new IllegalArgumentException("Unsupported transition " + expected);
        }
    }

    private TaskEventType eventTypeFor(TaskStatus expected) {
        return switch (expected) {
            case RUNNING -> TaskEventType.TASK_STARTED;
            case SUCCEEDED -> TaskEventType.TASK_SUCCEEDED;
            case FAILED -> TaskEventType.TASK_FAILED;
            case CANCELLED -> TaskEventType.TASK_CANCELLED;
            case QUEUED -> TaskEventType.TASK_STARTED;
            case WAITING_FOR_APPROVAL -> TaskEventType.TASK_APPROVAL_REQUIRED;
        };
    }
}
