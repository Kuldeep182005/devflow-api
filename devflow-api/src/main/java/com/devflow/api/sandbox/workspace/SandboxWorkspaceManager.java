package com.devflow.api.sandbox.workspace;

import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeFile;
import com.devflow.api.proposedchange.domain.ProposedChangeOperation;
import com.devflow.api.sandbox.config.SandboxProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SandboxWorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(SandboxWorkspaceManager.class);
    private final SandboxProperties properties;

    public SandboxWorkspaceManager(SandboxProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        properties.validate();
    }

    /**
     * Prepares an isolated execution workspace for the specified execution.
     * Copies base repository snapshot (if present) and applies the proposed change.
     */
    public Path prepareWorkspace(UUID executionId, Path baseRepositoryRoot, ProposedChange proposedChange) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Path configuredRoot = getSanitizedConfiguredRoot();
        Path executionWorkspace = configuredRoot.resolve(executionId.toString()).normalize();

        if (!executionWorkspace.startsWith(configuredRoot) || executionWorkspace.equals(configuredRoot)) {
            throw new SandboxWorkspaceException("WORKSPACE_ESCAPE", "Invalid execution workspace destination");
        }

        cleanupWorkspace(executionWorkspace);

        try {
            Files.createDirectories(executionWorkspace);
        } catch (IOException e) {
            throw new SandboxWorkspaceException("WORKSPACE_CREATE_FAILED", "Failed to create execution workspace", e);
        }

        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicLong totalBytes = new AtomicLong(0L);

        // 1. Copy base repository files if provided
        if (baseRepositoryRoot != null && Files.isDirectory(baseRepositoryRoot)) {
            copyBaseRepository(baseRepositoryRoot, executionWorkspace, fileCount, totalBytes);
        }

        // 2. Apply proposed change if provided
        if (proposedChange != null && proposedChange.getFiles() != null) {
            applyProposedChange(executionWorkspace, proposedChange, fileCount, totalBytes);
        }

        return executionWorkspace;
    }

    private void copyBaseRepository(Path sourceRoot, Path targetRoot, AtomicInteger fileCount, AtomicLong totalBytes) {
        Path normalizedSourceRoot = sourceRoot.toAbsolutePath().normalize();

        try {
            Files.walkFileTree(normalizedSourceRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (Files.isSymbolicLink(dir)) {
                        throw new SandboxWorkspaceException("SYMLINK_REJECTED", "Symbolic link directory rejected: " + dir);
                    }
                    Path rel = normalizedSourceRoot.relativize(dir);
                    if (rel.startsWith(".git") || rel.toString().equals(".git")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Path destDir = targetRoot.resolve(rel.toString()).normalize();
                    validateWorkspaceConfinement(destDir, targetRoot);
                    if (!Files.exists(destDir)) {
                        Files.createDirectories(destDir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (Files.isSymbolicLink(file)) {
                        throw new SandboxWorkspaceException("SYMLINK_REJECTED", "Symbolic link file rejected: " + file);
                    }
                    Path rel = normalizedSourceRoot.relativize(file);
                    if (rel.startsWith(".git") || rel.toString().equals(".git")) {
                        return FileVisitResult.CONTINUE;
                    }

                    long fileSize = attrs.size();
                    if (fileSize > properties.getMaxIndividualFileBytes()) {
                        throw new SandboxWorkspaceException("FILE_SIZE_EXCEEDED", "File exceeds individual file size limit: " + rel);
                    }
                    if (fileCount.incrementAndGet() > properties.getMaxFiles()) {
                        throw new SandboxWorkspaceException("MAX_FILES_EXCEEDED", "Workspace exceeds maximum file limit");
                    }
                    if (totalBytes.addAndGet(fileSize) > properties.getMaxWorkspaceBytes()) {
                        throw new SandboxWorkspaceException("WORKSPACE_SIZE_EXCEEDED", "Workspace exceeds maximum size limit");
                    }

                    Path destFile = targetRoot.resolve(rel.toString()).normalize();
                    validateWorkspaceConfinement(destFile, targetRoot);
                    Files.copy(file, destFile);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new SandboxWorkspaceException("WORKSPACE_COPY_FAILED", "Failed to copy base repository to workspace", e);
        }
    }

    private void applyProposedChange(Path executionWorkspace, ProposedChange proposedChange,
                                      AtomicInteger fileCount, AtomicLong totalBytes) {
        for (ProposedChangeFile file : proposedChange.getFiles()) {
            validatePathSecurity(file.getPath());

            Path target = executionWorkspace.resolve(file.getPath()).normalize();
            validateWorkspaceConfinement(target, executionWorkspace);

            ProposedChangeOperation operation = file.getOperation();
            if (operation == ProposedChangeOperation.DELETE) {
                try {
                    if (Files.exists(target)) {
                        if (Files.isSymbolicLink(target)) {
                            throw new SandboxWorkspaceException("SYMLINK_REJECTED", "Target file is a symbolic link");
                        }
                        long size = Files.size(target);
                        Files.delete(target);
                        fileCount.decrementAndGet();
                        totalBytes.addAndGet(-size);
                    }
                } catch (IOException e) {
                    throw new SandboxWorkspaceException("PATCH_APPLICATION_FAILED", "Failed to delete file: " + file.getPath(), e);
                }
            } else if (operation == ProposedChangeOperation.ADD) {
                try {
                    Path parent = target.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        validateWorkspaceConfinement(parent, executionWorkspace);
                        Files.createDirectories(parent);
                    }
                    String addedContent = UnifiedDiffApplier.extractAddedContent(file.getFilePatch());
                    byte[] bytes = addedContent.getBytes(StandardCharsets.UTF_8);
                    if (bytes.length > properties.getMaxIndividualFileBytes()) {
                        throw new SandboxWorkspaceException("FILE_SIZE_EXCEEDED", "Added file exceeds size limit: " + file.getPath());
                    }
                    if (fileCount.incrementAndGet() > properties.getMaxFiles()) {
                        throw new SandboxWorkspaceException("MAX_FILES_EXCEEDED", "Workspace exceeds maximum file limit");
                    }
                    if (totalBytes.addAndGet(bytes.length) > properties.getMaxWorkspaceBytes()) {
                        throw new SandboxWorkspaceException("WORKSPACE_SIZE_EXCEEDED", "Workspace exceeds maximum size limit");
                    }
                    Files.write(target, bytes);
                } catch (IOException e) {
                    throw new SandboxWorkspaceException("PATCH_APPLICATION_FAILED", "Failed to add file: " + file.getPath(), e);
                }
            } else if (operation == ProposedChangeOperation.MODIFY) {
                try {
                    if (!Files.exists(target) || !Files.isRegularFile(target)) {
                        throw new SandboxWorkspaceException("PATCH_APPLICATION_FAILED", "Target file to modify does not exist: " + file.getPath());
                    }
                    if (Files.isSymbolicLink(target)) {
                        throw new SandboxWorkspaceException("SYMLINK_REJECTED", "Target file is a symbolic link");
                    }
                    long oldSize = Files.size(target);
                    String existingContent = Files.readString(target, StandardCharsets.UTF_8);
                    String modifiedContent = UnifiedDiffApplier.applyPatch(existingContent, file.getFilePatch());
                    byte[] bytes = modifiedContent.getBytes(StandardCharsets.UTF_8);
                    if (bytes.length > properties.getMaxIndividualFileBytes()) {
                        throw new SandboxWorkspaceException("FILE_SIZE_EXCEEDED", "Modified file exceeds size limit: " + file.getPath());
                    }
                    long delta = bytes.length - oldSize;
                    if (totalBytes.addAndGet(delta) > properties.getMaxWorkspaceBytes()) {
                        throw new SandboxWorkspaceException("WORKSPACE_SIZE_EXCEEDED", "Workspace exceeds maximum size limit");
                    }
                    Files.write(target, bytes);
                } catch (Exception e) {
                    if (e instanceof SandboxWorkspaceException swe) {
                        throw swe;
                    }
                    throw new SandboxWorkspaceException("PATCH_APPLICATION_FAILED", "Failed to patch file: " + file.getPath(), e);
                }
            }
        }
    }

    /**
     * Validates that a file path contains no directory traversal, null bytes, absolute paths,
     * Windows drive letters, or UNC paths.
     */
    public static void validatePathSecurity(String path) {
        if (path == null || path.isBlank()) {
            throw new SandboxWorkspaceException("INVALID_PATH", "Path must not be blank");
        }
        if (path.contains("\0")) {
            throw new SandboxWorkspaceException("INVALID_PATH", "Path contains null byte");
        }
        if (path.startsWith("/") || path.startsWith("\\")) {
            throw new SandboxWorkspaceException("INVALID_PATH", "Absolute paths are prohibited: " + path);
        }
        if (path.matches("^[a-zA-Z]:.*")) {
            throw new SandboxWorkspaceException("INVALID_PATH", "Windows drive paths are prohibited: " + path);
        }
        if (path.startsWith("\\\\") || path.startsWith("//")) {
            throw new SandboxWorkspaceException("INVALID_PATH", "UNC paths are prohibited: " + path);
        }
        String normalized = path.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if (segment.equals("..")) {
                throw new SandboxWorkspaceException("INVALID_PATH", "Parent path traversal (..) prohibited: " + path);
            }
        }
    }

    private void validateWorkspaceConfinement(Path target, Path workspaceRoot) {
        Path normTarget = target.normalize().toAbsolutePath();
        Path normRoot = workspaceRoot.normalize().toAbsolutePath();
        if (!normTarget.startsWith(normRoot)) {
            throw new SandboxWorkspaceException("WORKSPACE_ESCAPE", "Target path escapes workspace: " + target);
        }
    }

    public void cleanupWorkspace(Path workspaceDir) {
        if (workspaceDir == null || !Files.exists(workspaceDir)) {
            return;
        }
        Path configuredRoot = getSanitizedConfiguredRoot();
        Path normDir = workspaceDir.normalize().toAbsolutePath();
        if (!normDir.startsWith(configuredRoot) || normDir.equals(configuredRoot)) {
            log.warn("Refusing to delete path outside configured workspace root: {}", workspaceDir);
            return;
        }
        try (Stream<Path> walk = Files.walk(normDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.debug("Failed deleting path during cleanup: {}", path);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed traversing workspace directory for cleanup: {}", workspaceDir, e);
        }
    }

    public void cleanupWorkspace(UUID executionId) {
        if (executionId == null) {
            return;
        }
        Path configuredRoot = getSanitizedConfiguredRoot();
        cleanupWorkspace(configuredRoot.resolve(executionId.toString()));
    }

    private Path getSanitizedConfiguredRoot() {
        return Path.of(properties.getWorkspaceRoot()).toAbsolutePath().normalize();
    }
}
