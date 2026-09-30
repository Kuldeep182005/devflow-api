package com.devflow.api.sandbox.executor;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Provider-independent interface for isolated code execution.
 * Untrusted repository code MUST only execute inside implementations of this interface.
 */
public interface SandboxExecutor {

    /**
     * Executes the requested profile against the isolated temporary workspace.
     *
     * @param request server-derived execution request
     * @param workspaceDirectory path to isolated temporary workspace directory
     * @return bounded execution result
     */
    SandboxExecutionResult execute(SandboxExecutionRequest request, Path workspaceDirectory);

    /**
     * Cancels an ongoing execution, cleanly terminating isolated processes/containers
     * and freeing resources.
     *
     * @param executionId execution identifier
     */
    void cancel(UUID executionId);
}
