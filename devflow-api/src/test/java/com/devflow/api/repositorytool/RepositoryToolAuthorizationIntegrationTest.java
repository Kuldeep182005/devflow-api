package com.devflow.api.repositorytool;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.organization.OrganizationRepository;
import com.devflow.api.persistence.repository.RepositoryRepository;
import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.repositorytool.config.RepositoryToolConfiguration;
import com.devflow.api.repositorytool.config.RepositoryToolProperties;
import com.devflow.api.repositorytool.exception.RepositoryToolException;
import com.devflow.api.repositorytool.service.RepositoryToolService;
import com.devflow.api.repositorytool.workspace.WorkspaceResolver;
import com.devflow.api.repositorytool.security.PathSecurity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RepositoryToolConfiguration.class, RepositoryToolService.class, WorkspaceResolver.class, PathSecurity.class})
class RepositoryToolAuthorizationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @TempDir
    Path tempRoot;

    @Autowired UserRepository userRepository;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired OrganizationMemberRepository memberRepository;
    @Autowired RepositoryRepository repositoryRepository;
    @Autowired RepositoryToolProperties properties;
    @Autowired RepositoryToolService service;

    private UUID userA;
    private UUID userB;
    private UUID organizationA;
    private UUID organizationB;
    private UUID repositoryA;
    private UUID repositoryB;

    @BeforeEach
    void setUp() throws Exception {
        Instant now = Instant.now();
        var savedUserA = userRepository.saveAndFlush(
                new com.devflow.api.user.domain.User("tool-a-" + UUID.randomUUID() + "@example.com",
                        "A", "tool-a-" + UUID.randomUUID(), true, now, now));
        var savedUserB = userRepository.saveAndFlush(
                new com.devflow.api.user.domain.User("tool-b-" + UUID.randomUUID() + "@example.com",
                        "B", "tool-b-" + UUID.randomUUID(), true, now, now));
        var savedOrganizationA = organizationRepository.saveAndFlush(
                new Organization("Tool A", "tool-a-" + UUID.randomUUID(), now, now));
        var savedOrganizationB = organizationRepository.saveAndFlush(
                new Organization("Tool B", "tool-b-" + UUID.randomUUID(), now, now));
        memberRepository.saveAndFlush(new OrganizationMember(savedOrganizationA, savedUserA,
                OrganizationRole.OWNER, now));
        memberRepository.saveAndFlush(new OrganizationMember(savedOrganizationB, savedUserB,
                OrganizationRole.OWNER, now));
        var savedRepositoryA = repositoryRepository.saveAndFlush(new Repository(savedOrganizationA,
                RepositoryProvider.GITHUB, "tool-a-" + UUID.randomUUID(), "tool/a", "main", true, now, now));
        var savedRepositoryB = repositoryRepository.saveAndFlush(new Repository(savedOrganizationB,
                RepositoryProvider.GITHUB, "tool-b-" + UUID.randomUUID(), "tool/b", "main", true, now, now));
        userA = savedUserA.getId();
        userB = savedUserB.getId();
        organizationA = savedOrganizationA.getId();
        organizationB = savedOrganizationB.getId();
        repositoryA = savedRepositoryA.getId();
        repositoryB = savedRepositoryB.getId();

        Path workspaceA = Files.createDirectories(tempRoot.resolve("a"));
        Path workspaceB = Files.createDirectories(tempRoot.resolve("b"));
        Files.writeString(workspaceA.resolve("a.txt"), "A");
        Files.writeString(workspaceB.resolve("b.txt"), "B");
        properties.setEnabled(true);
        properties.getWorkspaces().clear();
        properties.getWorkspaces().put(repositoryA.toString(), workspace(workspaceA));
        properties.getWorkspaces().put(repositoryB.toString(), workspace(workspaceB));
    }

    @Test
    void usersCannotUseCrossOrganizationRepositoryIdentifiers() {
        assertDenied(() -> service.listFiles(userA, organizationB, repositoryB, "", 10));
        assertDenied(() -> service.readFile(userA, organizationB, repositoryB, "b.txt"));
        assertDenied(() -> service.search(userA, organizationB, repositoryB, "B", null));
        assertDenied(() -> service.metadata(userA, organizationB, repositoryB));

        assertDenied(() -> service.listFiles(userB, organizationA, repositoryA, "", 10));
        assertDenied(() -> service.readFile(userB, organizationA, repositoryA, "a.txt"));
        assertDenied(() -> service.search(userB, organizationA, repositoryA, "A", null));
        assertDenied(() -> service.metadata(userB, organizationA, repositoryA));
    }

    private RepositoryToolProperties.Workspace workspace(Path root) {
        var workspace = new RepositoryToolProperties.Workspace();
        workspace.setRoot(root);
        workspace.setBranch("main");
        return workspace;
    }

    private void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(RepositoryToolException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo("REPOSITORY_NOT_FOUND"));
    }
}
