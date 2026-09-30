package com.devflow.api.task.api;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.organization.OrganizationRepository;
import com.devflow.api.persistence.repository.RepositoryRepository;
import com.devflow.api.persistence.task.TaskEventRepository;
import com.devflow.api.persistence.task.TaskRepository;
import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.security.JwtService;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskActor;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.task.domain.TaskEventType;
import com.devflow.api.task.domain.TaskSourceType;
import com.devflow.api.task.domain.TaskStatus;
import com.devflow.api.user.domain.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "async.worker.enabled=false",
        "async.outbox.poll-interval-ms=60000"
})
@AutoConfigureMockMvc
@Testcontainers
class TaskControllerPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository memberRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskEventRepository eventRepository;

    @Autowired
    private JwtService jwtService;

    private Scenario scenario;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        User userA = userRepository.saveAndFlush(
                new User("api-a-" + now.toEpochMilli() + "@example.com", "User A", "api-a-" + now, true, now, now));
        User userB = userRepository.saveAndFlush(
                new User("api-b-" + now.toEpochMilli() + "@example.com", "User B", "api-b-" + now, true, now, now));
        Organization organizationA = organizationRepository.saveAndFlush(
                new Organization("Organization A", "api-a-" + now.toEpochMilli(), now, now));
        Organization organizationB = organizationRepository.saveAndFlush(
                new Organization("Organization B", "api-b-" + now.toEpochMilli(), now, now));
        memberRepository.saveAndFlush(new OrganizationMember(
                organizationA, userA, OrganizationRole.OWNER, now));
        memberRepository.saveAndFlush(new OrganizationMember(
                organizationB, userB, OrganizationRole.OWNER, now));
        Repository repositoryA = repositoryRepository.saveAndFlush(new Repository(
                organizationA, RepositoryProvider.GITHUB, "api-repo-a-" + now.toEpochMilli(),
                "api/a", "main", true, now, now));
        Repository repositoryB = repositoryRepository.saveAndFlush(new Repository(
                organizationB, RepositoryProvider.GITHUB, "api-repo-b-" + now.toEpochMilli(),
                "api/b", "main", true, now, now));
        Task taskA = taskRepository.saveAndFlush(new Task(
                organizationA, repositoryA, userA, TaskSourceType.MANUAL, null,
                "Task A", null, TaskStatus.QUEUED, null, null, null, now, now, null, null));
        Task taskB = taskRepository.saveAndFlush(new Task(
                organizationB, repositoryB, userB, TaskSourceType.MANUAL, null,
                "Task B", null, TaskStatus.QUEUED, null, null, null, now, now, null, null));

        eventRepository.saveAllAndFlush(List.of(
                new TaskEvent(taskA, organizationA, TaskEventType.TASK_CREATED, null,
                        TaskStatus.QUEUED, TaskActor.user(userA.getId()), now.minusSeconds(30), null, null),
                new TaskEvent(taskA, organizationA, TaskEventType.TASK_STARTED, TaskStatus.QUEUED,
                        TaskStatus.RUNNING, TaskActor.worker(), now.minusSeconds(20), null, null),
                new TaskEvent(taskA, organizationA, TaskEventType.TASK_APPROVAL_REQUIRED,
                        TaskStatus.RUNNING, TaskStatus.WAITING_FOR_APPROVAL, TaskActor.worker(),
                        now.minusSeconds(10), null, null),
                new TaskEvent(taskB, organizationB, TaskEventType.TASK_CREATED, null,
                        TaskStatus.QUEUED, TaskActor.user(userB.getId()), now, null, null)));
        scenario = new Scenario(userA, organizationA, taskA, organizationB, taskB);
    }

    @Test
    void persistedEventsAreReturnedThroughRepositoryAndControllerPagination() throws Exception {
        String token = jwtService.generateToken(scenario.userA());
        var sorted = eventRepository.findAllByTaskIdAndOrganizationId(
                scenario.taskA().getId(), scenario.organizationA().getId(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        assertThat(sorted.getTotalElements()).isEqualTo(3);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/tasks/{taskId}/events",
                        scenario.organizationA().getId(), scenario.taskA().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(sorted.getContent().get(0).getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(sorted.getContent().get(1).getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(2));

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/tasks/{taskId}/events",
                        scenario.organizationA().getId(), scenario.taskA().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(sorted.getContent().get(2).getId().toString()));
    }

    @Test
    void persistedMultiOrganizationDataCannotBeAccessedThroughIdorPaths() throws Exception {
        String token = jwtService.generateToken(scenario.userA());
        assertThat(getWithToken(scenario.organizationB().getId(), scenario.taskB().getId(), token))
                .isEqualTo(404);
        assertThat(getWithToken(scenario.organizationA().getId(), scenario.taskB().getId(), token))
                .isEqualTo(404);
        assertThat(getWithToken(scenario.organizationB().getId(), scenario.taskA().getId(), token))
                .isEqualTo(404);
        assertThat(mockMvc.perform(post("/api/v1/organizations/{organizationId}/tasks/{taskId}/cancel",
                        scenario.organizationB().getId(), scenario.taskB().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get("/api/v1/organizations/{organizationId}/tasks/{taskId}/events",
                        scenario.organizationB().getId(), scenario.taskB().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    private int getWithToken(java.util.UUID organizationId, java.util.UUID taskId, String token)
            throws Exception {
        return mockMvc.perform(get("/api/v1/organizations/{organizationId}/tasks/{taskId}",
                        organizationId, taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private record Scenario(
            User userA,
            Organization organizationA,
            Task taskA,
            Organization organizationB,
            Task taskB) {
    }
}
