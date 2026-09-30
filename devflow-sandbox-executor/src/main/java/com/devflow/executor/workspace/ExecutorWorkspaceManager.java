package com.devflow.executor.workspace;

import com.devflow.executor.config.ExecutorProperties;
import com.devflow.executor.dto.ExecutionFileDto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manages the isolated execution workspace on the executor daemon host.
 * Strictly isolates files and prevents any host filesystem escape.
 */
@Component
public class ExecutorWorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(ExecutorWorkspaceManager.class);

    private final ExecutorProperties properties;

    public ExecutorWorkspaceManager(ExecutorProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public Path prepareWorkspace(UUID executionId, List<ExecutionFileDto> files) {
        Objects.requireNonNull(executionId, "executionId must not be null");

        Path root = Path.of(properties.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path workspace = root.resolve(executionId.toString()).normalize();

        if (!workspace.startsWith(root) || workspace.equals(root)) {
            throw new IllegalArgumentException("Invalid execution workspace path");
        }

        cleanupWorkspace(executionId);

        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create execution workspace directory", e);
        }

        if (files == null || files.isEmpty()) {
            return workspace;
        }

        if (files.size() > properties.getMaxWorkspaceFiles()) {
            throw new IllegalArgumentException("File count exceeds maximum allowed files (" + properties.getMaxWorkspaceFiles() + ")");
        }

        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicLong totalBytes = new AtomicLong(0L);

        for (ExecutionFileDto file : files) {
            validateAndWriteFile(workspace, file, fileCount, totalBytes);
        }

        return workspace;
    }

    private void validateAndWriteFile(Path workspace, ExecutionFileDto file, AtomicInteger fileCount, AtomicLong totalBytes) {
        if (file == null || file.filePath() == null) {
            throw new IllegalArgumentException("File and filePath must not be null");
        }

        String pathStr = file.filePath().trim();
        validatePathSecurity(pathStr);

        Path target = workspace.resolve(pathStr).normalize();
        if (!target.startsWith(workspace) || target.equals(workspace)) {
            throw new IllegalArgumentException("Path traversal detected: " + pathStr);
        }

        if ("DELETE".equalsIgnoreCase(file.operation())) {
            try {
                if (Files.exists(target)) {
                    if (Files.isSymbolicLink(target)) {
                        throw new IllegalArgumentException("Symbolic link detected: " + pathStr);
                    }
                    Files.delete(target);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to delete file " + pathStr, e);
            }
            return;
        }

        byte[] contentBytes = file.content() != null
                ? file.content().getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        if (contentBytes.length > properties.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException("File size exceeds maximum (" + properties.getMaxFileSizeBytes() + " bytes): " + pathStr);
        }

        long currentTotal = totalBytes.addAndGet(contentBytes.length);
        if (currentTotal > properties.getMaxWorkspaceBytes()) {
            throw new IllegalArgumentException("Total workspace size exceeds limit (" + properties.getMaxWorkspaceBytes() + " bytes)");
        }

        int currentFiles = fileCount.incrementAndGet();
        if (currentFiles > properties.getMaxWorkspaceFiles()) {
            throw new IllegalArgumentException("Total workspace file count exceeds limit (" + properties.getMaxWorkspaceFiles() + ")");
        }

        try {
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            if (Files.isSymbolicLink(target)) {
                throw new IllegalArgumentException("Symbolic link target detected: " + pathStr);
            }
            Files.write(target, contentBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write workspace file " + pathStr, e);
        }
    }

    private void validatePathSecurity(String path) {
        if (path.isEmpty() || path.contains("\0")) {
            throw new IllegalArgumentException("Path contains invalid characters");
        }
        if (path.startsWith("/") || path.startsWith("\\")) {
            throw new IllegalArgumentException("Absolute paths are forbidden");
        }
        if (path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
            throw new IllegalArgumentException("Windows drive letters are forbidden");
        }
        if (path.startsWith("\\\\") || path.startsWith("//")) {
            throw new IllegalArgumentException("UNC network paths are forbidden");
        }
        String normalized = path.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("Path traversal (..) is forbidden");
            }
        }
    }

    public void cleanupWorkspace(UUID executionId) {
        if (executionId == null) {
            return;
        }
        Path root = Path.of(properties.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path workspace = root.resolve(executionId.toString()).normalize();
        cleanupDirectory(workspace);
    }

    public void cleanupDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.debug("Could not delete workspace path {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean up workspace directory {}: {}", dir, e.getMessage());
        }
    }
}
