package com.devflow.api.sandbox;

import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeFile;
import com.devflow.api.proposedchange.domain.ProposedChangeOperation;
import com.devflow.api.sandbox.config.SandboxProperties;
import com.devflow.api.sandbox.workspace.SandboxWorkspaceException;
import com.devflow.api.sandbox.workspace.SandboxWorkspaceManager;
import com.devflow.api.sandbox.workspace.UnifiedDiffApplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SandboxWorkspaceSecurityTest {

    @TempDir
    Path tempDir;

    private SandboxProperties properties;
    private SandboxWorkspaceManager workspaceManager;

    @BeforeEach
    void setUp() {
        properties = new SandboxProperties();
        properties.setWorkspaceRoot(tempDir.resolve("workspaces").toString());
        properties.setMaxFiles(5);
        properties.setMaxIndividualFileBytes(1024); // 1KB
        properties.setMaxWorkspaceBytes(4096); // 4KB
        properties.validate();
        workspaceManager = new SandboxWorkspaceManager(properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../secret.txt",
            "foo/../../secret.txt",
            "..\\secret.txt",
            "foo\\..\\..\\secret.txt",
            "/etc/passwd",
            "\\Windows\\System32",
            "C:\\foo\\bar.txt",
            "d:/bar/baz.txt",
            "\\\\server\\share\\file.txt",
            "//server/share/file.txt",
            "foo\0bar.txt"
    })
    @DisplayName("Rejects dangerous paths and traversals")
    void rejectsDangerousPaths(String dangerousPath) {
        assertThatThrownBy(() -> SandboxWorkspaceManager.validatePathSecurity(dangerousPath))
                .isInstanceOf(SandboxWorkspaceException.class);
    }

    @Test
    @DisplayName("Prepares clean execution workspace inside configured root")
    void preparesCleanExecutionWorkspace() {
        UUID executionId = UUID.randomUUID();
        Path workspace = workspaceManager.prepareWorkspace(executionId, null, null);

        assertThat(workspace).exists();
        assertThat(workspace.startsWith(Path.of(properties.getWorkspaceRoot()).toAbsolutePath().normalize())).isTrue();
        assertThat(workspace.getFileName().toString()).isEqualTo(executionId.toString());

        workspaceManager.cleanupWorkspace(executionId);
        assertThat(workspace).doesNotExist();
    }

    @Test
    @DisplayName("Rejects symlinks in source base repository")
    void rejectsSymlinksInBaseRepository() throws IOException {
        Path baseRepo = tempDir.resolve("base-repo");
        Files.createDirectories(baseRepo);
        Path target = baseRepo.resolve("target.txt");
        Files.writeString(target, "real content");

        Path link = baseRepo.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            // Some Windows environments without SeCreateSymbolicLinkPrivilege may fail to create symlink
            return;
        }

        UUID executionId = UUID.randomUUID();
        assertThatThrownBy(() -> workspaceManager.prepareWorkspace(executionId, baseRepo, null))
                .isInstanceOf(SandboxWorkspaceException.class)
                .hasFieldOrPropertyWithValue("failureCode", "SYMLINK_REJECTED");
    }

    @Test
    @DisplayName("Rejects base repository exceeding maximum file count")
    void rejectsExcessiveFileCount() throws IOException {
        Path baseRepo = tempDir.resolve("big-repo");
        Files.createDirectories(baseRepo);

        for (int i = 0; i < 10; i++) {
            Files.writeString(baseRepo.resolve("file" + i + ".txt"), "data");
        }

        UUID executionId = UUID.randomUUID();
        assertThatThrownBy(() -> workspaceManager.prepareWorkspace(executionId, baseRepo, null))
                .isInstanceOf(SandboxWorkspaceException.class)
                .hasFieldOrPropertyWithValue("failureCode", "MAX_FILES_EXCEEDED");
    }

    @Test
    @DisplayName("Rejects base repository file exceeding individual file size")
    void rejectsExcessiveIndividualFileSize() throws IOException {
        Path baseRepo = tempDir.resolve("large-file-repo");
        Files.createDirectories(baseRepo);

        byte[] largeBytes = new byte[2048]; // exceeds 1024B limit
        Files.write(baseRepo.resolve("large.txt"), largeBytes);

        UUID executionId = UUID.randomUUID();
        assertThatThrownBy(() -> workspaceManager.prepareWorkspace(executionId, baseRepo, null))
                .isInstanceOf(SandboxWorkspaceException.class)
                .hasFieldOrPropertyWithValue("failureCode", "FILE_SIZE_EXCEEDED");
    }

    @Test
    @DisplayName("Applies ProposedChange ADD operation in pure Java")
    void appliesAddOperation() throws IOException {
        ProposedChange change = mock(ProposedChange.class);
        ProposedChangeFile file = new ProposedChangeFile(
                "src/Hello.java",
                ProposedChangeOperation.ADD,
                3,
                0,
                """
                diff --git a/src/Hello.java b/src/Hello.java
                new file mode 100644
                --- /dev/null
                +++ b/src/Hello.java
                @@ -0,0 +1,3 @@
                +public class Hello {
                +    // added
                +}
                """
        );
        when(change.getFiles()).thenReturn(java.util.List.of(file));

        UUID executionId = UUID.randomUUID();
        Path workspace = workspaceManager.prepareWorkspace(executionId, null, change);

        Path addedFile = workspace.resolve("src/Hello.java");
        assertThat(addedFile).exists();
        String content = Files.readString(addedFile, StandardCharsets.UTF_8);
        assertThat(content).contains("public class Hello").contains("// added");

        workspaceManager.cleanupWorkspace(executionId);
    }

    @Test
    @DisplayName("Applies ProposedChange MODIFY operation in pure Java")
    void appliesModifyOperation() throws IOException {
        Path baseRepo = tempDir.resolve("modify-repo");
        Files.createDirectories(baseRepo.resolve("src"));
        Files.writeString(baseRepo.resolve("src/App.java"), """
        public class App {
            public int value() { return 1; }
        }
        """);

        ProposedChange change = mock(ProposedChange.class);
        ProposedChangeFile file = new ProposedChangeFile(
                "src/App.java",
                ProposedChangeOperation.MODIFY,
                1,
                1,
                """
                diff --git a/src/App.java b/src/App.java
                --- a/src/App.java
                +++ b/src/App.java
                @@ -2,1 +2,1 @@
                -    public int value() { return 1; }
                +    public int value() { return 42; }
                """
        );
        when(change.getFiles()).thenReturn(java.util.List.of(file));

        UUID executionId = UUID.randomUUID();
        Path workspace = workspaceManager.prepareWorkspace(executionId, baseRepo, change);

        Path patchedFile = workspace.resolve("src/App.java");
        assertThat(patchedFile).exists();
        String content = Files.readString(patchedFile, StandardCharsets.UTF_8);
        assertThat(content).contains("return 42;").doesNotContain("return 1;");

        workspaceManager.cleanupWorkspace(executionId);
    }

    @Test
    @DisplayName("Applies ProposedChange DELETE operation in pure Java")
    void appliesDeleteOperation() throws IOException {
        Path baseRepo = tempDir.resolve("delete-repo");
        Files.createDirectories(baseRepo);
        Files.writeString(baseRepo.resolve("obsolete.txt"), "delete me");

        ProposedChange change = mock(ProposedChange.class);
        ProposedChangeFile file = new ProposedChangeFile(
                "obsolete.txt",
                ProposedChangeOperation.DELETE,
                0,
                1,
                """
                diff --git a/obsolete.txt b/obsolete.txt
                deleted file mode 100644
                --- a/obsolete.txt
                +++ /dev/null
                @@ -1,1 +0,0 @@
                -delete me
                """
        );
        when(change.getFiles()).thenReturn(java.util.List.of(file));

        UUID executionId = UUID.randomUUID();
        Path workspace = workspaceManager.prepareWorkspace(executionId, baseRepo, change);

        Path deletedFile = workspace.resolve("obsolete.txt");
        assertThat(deletedFile).doesNotExist();

        workspaceManager.cleanupWorkspace(executionId);
    }
}
