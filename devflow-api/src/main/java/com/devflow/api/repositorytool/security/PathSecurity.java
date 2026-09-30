package com.devflow.api.repositorytool.security;

import com.devflow.api.repositorytool.domain.RepositoryWorkspace;
import com.devflow.api.repositorytool.exception.RepositoryToolExceptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PathSecurity {

    public Path resolve(RepositoryWorkspace workspace, String relativePath, int maxDepth, boolean allowRoot) {
        if (relativePath == null || relativePath.indexOf('\0') >= 0) {
            throw RepositoryToolExceptions.invalidPath();
        }
        if (relativePath.isBlank()) {
            if (allowRoot) {
                return workspace.root();
            }
            throw RepositoryToolExceptions.invalidPath();
        }
        String portable = relativePath.replace('\\', '/');
        if (portable.startsWith("/") || portable.matches("^[A-Za-z]:.*")
                || portable.startsWith("//") || portable.contains("//")) {
            throw RepositoryToolExceptions.invalidPath();
        }
        List<String> segments = List.of(portable.split("/", -1));
        int depth = 0;
        for (String segment : segments) {
            if (segment.isBlank()) {
                throw RepositoryToolExceptions.invalidPath();
            }
            if (".".equals(segment)) {
                continue;
            }
            depth++;
            if ("..".equals(segment) || depth > maxDepth) {
                throw RepositoryToolExceptions.outsideWorkspace();
            }
        }
        Path candidate = workspace.root().resolve(portable).normalize();
        if (!candidate.startsWith(workspace.root())) {
            throw RepositoryToolExceptions.outsideWorkspace();
        }
        rejectLinks(workspace.root(), candidate);
        try {
            Path existing = Files.exists(candidate, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    ? candidate.toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    : candidate.getParent() == null ? workspace.root()
                            : candidate.getParent().toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (!existing.startsWith(workspace.root())) {
                throw RepositoryToolExceptions.symlinkEscape();
            }
        } catch (IOException exception) {
            throw RepositoryToolExceptions.workspaceUnavailable();
        }
        return candidate;
    }

    private void rejectLinks(Path root, Path candidate) {
        Path current = root;
        Path relative = root.relativize(candidate);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw RepositoryToolExceptions.symlinkEscape();
            }
        }
    }
}
