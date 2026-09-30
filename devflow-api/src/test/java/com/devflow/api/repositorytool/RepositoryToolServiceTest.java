package com.devflow.api.repositorytool;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.repository.RepositoryRepository;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.repositorytool.api.RepositoryFileType;
import com.devflow.api.repositorytool.config.RepositoryToolProperties;
import com.devflow.api.repositorytool.exception.RepositoryToolException;
import com.devflow.api.repositorytool.service.RepositoryToolService;
import com.devflow.api.repositorytool.workspace.WorkspaceResolver;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RepositoryToolServiceTest {

    @TempDir
    Path workspaceRoot;

    @Mock
    RepositoryRepository repositoryRepository;

    @Mock
    OrganizationMemberRepository memberRepository;

    private final UUID userId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private UUID repositoryId;
    private RepositoryToolProperties properties;
    private RepositoryToolService service;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("src"));
        Files.writeString(workspaceRoot.resolve("z.txt"), "last");
        Files.writeString(workspaceRoot.resolve("a.txt"), "first\nneedle here\n");
        Files.writeString(workspaceRoot.resolve("src/Main.java"), "class Main { }\n");

        Organization organization = new Organization("Org", "org", Instant.now(), Instant.now());
        setId(organization, organizationId);
        OrganizationMember member = org.mockito.Mockito.mock(OrganizationMember.class);
        lenient().when(member.getOrganization()).thenReturn(organization);
        lenient().when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));

        Repository repository = new Repository(organization, RepositoryProvider.GITHUB, "external",
                "org/repo", "main", true, Instant.now(), Instant.now());
        repositoryId = UUID.randomUUID();
        setId(repository, repositoryId);
        lenient().when(repositoryRepository.findByIdAndOrganizationId(repositoryId, organizationId))
                .thenReturn(Optional.of(repository));

        properties = new RepositoryToolProperties();
        properties.setEnabled(true);
        properties.setMaxFileBytes(1024);
        properties.setMaxListResults(10);
        properties.setMaxSearchResults(10);
        properties.setMaxFilesScanned(20);
        properties.setMaxSnippetCharacters(10);
        properties.setMaxSearchQueryCharacters(20);
        properties.getWorkspaces().put(repositoryId.toString(), workspace(workspaceRoot));
        service = new RepositoryToolService(repositoryRepository, memberRepository, properties,
                new WorkspaceResolver(properties));
    }

    @Test
    void listsFilesDeterministicallyAndReturnsRelativePaths() {
        var entries = service.listFiles(userId, organizationId, repositoryId, "", 10);

        assertThat(entries).extracting(entry -> entry.path())
                .containsExactly("a.txt", "src", "z.txt");
        assertThat(entries).extracting(entry -> entry.type())
                .containsExactly(RepositoryFileType.FILE, RepositoryFileType.DIRECTORY, RepositoryFileType.FILE);
        assertThat(entries).allSatisfy(entry -> assertThat(entry.path()).doesNotContain("\\"));
    }

    @Test
    void readsNestedTextFile() {
        var result = service.readFile(userId, organizationId, repositoryId, "src\\Main.java");

        assertThat(result.path()).isEqualTo("src/Main.java");
        assertThat(result.content()).contains("class Main");
    }

    @Test
    void searchesLiteralTextWithLineAndBoundedSnippet() {
        var matches = service.search(userId, organizationId, repositoryId, "needle", null);

        assertThat(matches).singleElement()
                .satisfies(match -> {
                    assertThat(match.path()).isEqualTo("a.txt");
                    assertThat(match.lineNumber()).isEqualTo(2);
                });
    }

    @Test
    void returnsSafeMetadata() {
        var metadata = service.metadata(userId, organizationId, repositoryId);

        assertThat(metadata.repositoryId()).isEqualTo(repositoryId);
        assertThat(metadata.repositoryName()).isEqualTo("org/repo");
        assertThat(metadata.defaultBranch()).isEqualTo("main");
        assertThat(metadata.configuredBranch()).isEqualTo("main");
    }

    @Test
    void rejectsTraversalAndAbsoluteWindowsAndUnixPaths() {
        for (String path : List.of("../secret", "..\\secret", "../../secret", "..\\..\\secret",
                "/etc/passwd", "C:\\Users\\Owner\\secret", "C:/Users/Owner/secret", "\\\\server\\share\\secret",
                "src//Main.java", "src\0/Main.java")) {
            assertThatThrownBy(() -> service.readFile(userId, organizationId, repositoryId, path))
                    .isInstanceOf(RepositoryToolException.class);
        }
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> service.readFile(userId, organizationId, repositoryId, "missing.txt"))
                .isInstanceOfSatisfying(RepositoryToolException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("FILE_NOT_FOUND"));
    }

    @Test
    void enforcesFileSizeAndSearchQueryLimits() throws Exception {
        Files.writeString(workspaceRoot.resolve("large.txt"), "0123456789");
        properties.setMaxFileBytes(5);
        assertThatThrownBy(() -> service.readFile(userId, organizationId, repositoryId, "large.txt"))
                .isInstanceOfSatisfying(RepositoryToolException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("FILE_TOO_LARGE"));

        properties.setMaxSearchQueryCharacters(3);
        assertThatThrownBy(() -> service.search(userId, organizationId, repositoryId, "long", null))
                .isInstanceOfSatisfying(RepositoryToolException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_SEARCH_QUERY"));
    }

    @Test
    void enforcesListSearchAndScanBounds() throws Exception {
        properties.setMaxListResults(1);
        assertThat(service.listFiles(userId, organizationId, repositoryId, "", 20)).hasSize(1);

        properties.setMaxSearchResults(1);
        assertThatThrownBy(() -> service.search(userId, organizationId, repositoryId, "class", null))
                .isInstanceOfSatisfying(RepositoryToolException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SEARCH_LIMIT_EXCEEDED"));

        properties.setMaxFilesScanned(1);
        assertThatThrownBy(() -> service.search(userId, organizationId, repositoryId, "never-present", null))
                .isInstanceOfSatisfying(RepositoryToolException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SEARCH_LIMIT_EXCEEDED"));
    }

    @Test
    void scanBudgetIsCheckedBeforeOpeningTheNextFile() throws Exception {
        Path budget = Files.createDirectories(workspaceRoot.resolve("budget"));
        Files.writeString(budget.resolve("a.txt"), "no match");
        Files.write(budget.resolve("b.bin"), new byte[] {0, 1, 2});
        properties.setMaxFilesScanned(1);

        assertThatThrownBy(() -> service.search(userId, organizationId, repositoryId, "absent", "budget"))
                .isInstanceOfSatisfying(RepositoryToolException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SEARCH_LIMIT_EXCEEDED"));
    }

    @Test
    void enforcesSnippetAndDirectoryDepthLimits() throws Exception {
        properties.setMaxSnippetCharacters(4);
        var matches = service.search(userId, organizationId, repositoryId, "needle", null);
        assertThat(matches).singleElement().satisfies(match ->
                assertThat(match.snippet()).hasSize(4));

        properties.setMaxDirectoryDepth(0);
        assertThatThrownBy(() -> service.search(userId, organizationId, repositoryId, "class", "src"))
                .isInstanceOf(RepositoryToolException.class);
    }

    @Test
    void workspaceResolutionUsesAuthorizedRepositoryIdOnly() throws Exception {
        UUID otherRepositoryId = UUID.randomUUID();
        Path otherRoot = Files.createTempDirectory("other-repository");
        properties.getWorkspaces().clear();
        properties.getWorkspaces().put(otherRepositoryId.toString(), workspace(otherRoot));

        assertThatThrownBy(() -> service.metadata(userId, organizationId, repositoryId))
                .isInstanceOfSatisfying(RepositoryToolException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("WORKSPACE_UNAVAILABLE"));
    }

    @Test
    void missingAuthorizedWorkspaceIsSafe() {
        properties.getWorkspaces().clear();

        assertThatThrownBy(() -> service.readFile(userId, organizationId, repositoryId, "a.txt"))
                .isInstanceOfSatisfying(RepositoryToolException.class,
                        exception -> {
                            assertThat(exception.getCode()).isEqualTo("WORKSPACE_UNAVAILABLE");
                            assertThat(exception.getMessage()).doesNotContain(workspaceRoot.toString());
                        });
    }

    @Test
    void rejectsInvalidConfiguredLimitsAndWorkspaceEntries() {
        properties.setMaxFilesScanned(0);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Repository tool limits are invalid");

        properties.setMaxFilesScanned(1);
        properties.getWorkspaces().put("invalid", new RepositoryToolProperties.Workspace());
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Repository tool workspaces are invalid");
    }

    @Test
    void rejectsSymlinkEscapeWhenPlatformAllowsCreatingOne() throws Exception {
        Path outside = Files.createTempDirectory("repository-tool-outside");
        Path link = workspaceRoot.resolve("outside-link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
            return;
        }
        Files.writeString(outside.resolve("secret.txt"), "secret");

        assertThatThrownBy(() -> service.readFile(userId, organizationId, repositoryId,
                "outside-link/secret.txt"))
                .isInstanceOf(RepositoryToolException.class);
    }

    @Test
    void rejectsBinaryAndMalformedUtf8Files() throws Exception {
        Files.write(workspaceRoot.resolve("binary.bin"), new byte[] {1, 2, 0, 3});
        Files.write(workspaceRoot.resolve("malformed.bin"), new byte[] {(byte) 0xc3, 0x28});

        for (String path : List.of("binary.bin", "malformed.bin")) {
            assertThatThrownBy(() -> service.readFile(userId, organizationId, repositoryId, path))
                    .isInstanceOfSatisfying(RepositoryToolException.class,
                            exception -> assertThat(exception.getCode()).isEqualTo("BINARY_FILE"));
        }
    }

    @Test
    void rejectsUnauthorizedRepositoryAndDisabledTools() {
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.metadata(userId, organizationId, repositoryId))
                .isInstanceOfSatisfying(RepositoryToolException.class,
                        exception -> assertThat(exception.getStatus().value()).isEqualTo(404));

        properties.setEnabled(false);
        assertThatThrownBy(() -> service.metadata(userId, organizationId, repositoryId))
                .isInstanceOfSatisfying(RepositoryToolException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("REPOSITORY_TOOLS_DISABLED"));
    }

    private RepositoryToolProperties.Workspace workspace(Path root) {
        RepositoryToolProperties.Workspace workspace = new RepositoryToolProperties.Workspace();
        workspace.setRoot(root);
        workspace.setBranch("main");
        return workspace;
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
