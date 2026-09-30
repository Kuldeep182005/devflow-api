package com.devflow.api.sandbox.exception;

import org.springframework.http.HttpStatus;

public final class SandboxExceptions {

    private SandboxExceptions() {
    }

    public static SandboxException notFound() {
        return new SandboxException(HttpStatus.NOT_FOUND, "SANDBOX_EXECUTION_NOT_FOUND",
                "The sandbox execution was not found");
    }

    public static SandboxException proposedChangeNotFound() {
        return new SandboxException(HttpStatus.NOT_FOUND, "PROPOSED_CHANGE_NOT_FOUND",
                "The proposed change was not found");
    }

    public static SandboxException invalidProposalStatus() {
        return new SandboxException(HttpStatus.BAD_REQUEST, "INVALID_PROPOSED_CHANGE_STATUS",
                "Only validated proposed changes may be executed in the sandbox");
    }

    public static SandboxException inactiveRepository() {
        return new SandboxException(HttpStatus.CONFLICT, "REPOSITORY_INACTIVE",
                "The repository is inactive");
    }

    public static SandboxException forbidden() {
        return new SandboxException(HttpStatus.FORBIDDEN, "SANDBOX_ACCESS_DENIED",
                "Access to the requested sandbox execution is denied");
    }

    public static SandboxException invalidProfile(String profile) {
        return new SandboxException(HttpStatus.BAD_REQUEST, "INVALID_EXECUTION_PROFILE",
                "Execution profile is not supported or not allowed: " + profile);
    }

    public static SandboxException disabled() {
        return new SandboxException(HttpStatus.SERVICE_UNAVAILABLE, "SANDBOX_DISABLED",
                "Sandbox execution is disabled");
    }
}
