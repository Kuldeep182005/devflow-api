package com.devflow.api.sandbox.workspace;

public class SandboxWorkspaceException extends RuntimeException {

    private final String failureCode;

    public SandboxWorkspaceException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public SandboxWorkspaceException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String getFailureCode() {
        return failureCode;
    }
}
