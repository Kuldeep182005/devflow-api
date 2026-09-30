package com.devflow.api.repositorytool.exception;

import org.springframework.http.HttpStatus;

public final class RepositoryToolExceptions {
    private RepositoryToolExceptions() {}

    public static RepositoryToolException disabled() {
        return new RepositoryToolException(HttpStatus.SERVICE_UNAVAILABLE, "REPOSITORY_TOOLS_DISABLED",
                "Repository tools are disabled");
    }
    public static RepositoryToolException notFound() {
        return new RepositoryToolException(HttpStatus.NOT_FOUND, "REPOSITORY_NOT_FOUND",
                "The repository does not exist");
    }
    public static RepositoryToolException unauthorized() {
        return new RepositoryToolException(HttpStatus.NOT_FOUND, "REPOSITORY_NOT_FOUND",
                "The repository does not exist");
    }
    public static RepositoryToolException workspaceUnavailable() {
        return new RepositoryToolException(HttpStatus.SERVICE_UNAVAILABLE, "WORKSPACE_UNAVAILABLE",
                "The repository workspace is unavailable");
    }
    public static RepositoryToolException invalidPath() {
        return new RepositoryToolException(HttpStatus.BAD_REQUEST, "INVALID_REPOSITORY_PATH",
                "The repository path is invalid");
    }
    public static RepositoryToolException outsideWorkspace() {
        return new RepositoryToolException(HttpStatus.BAD_REQUEST, "PATH_OUTSIDE_WORKSPACE",
                "The repository path is outside the workspace");
    }
    public static RepositoryToolException symlinkEscape() {
        return new RepositoryToolException(HttpStatus.BAD_REQUEST, "SYMLINK_ESCAPE",
                "The repository path crosses an unsafe link");
    }
    public static RepositoryToolException fileNotFound() {
        return new RepositoryToolException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND",
                "The repository file was not found");
    }
    public static RepositoryToolException fileTooLarge() {
        return new RepositoryToolException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "The repository file is too large");
    }
    public static RepositoryToolException binaryFile() {
        return new RepositoryToolException(HttpStatus.UNPROCESSABLE_ENTITY, "BINARY_FILE",
                "The repository file is not text");
    }
    public static RepositoryToolException invalidQuery() {
        return new RepositoryToolException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_QUERY",
                "The search query is invalid");
    }
    public static RepositoryToolException searchLimit() {
        return new RepositoryToolException(HttpStatus.PAYLOAD_TOO_LARGE, "SEARCH_LIMIT_EXCEEDED",
                "The search limits were exceeded");
    }
}
