package com.devflow.executor.controller;

import com.devflow.executor.config.ExecutorProperties;
import com.devflow.executor.docker.DockerSandboxExecutor;
import com.devflow.executor.dto.CancelRequestDto;
import com.devflow.executor.dto.ExecuteRequestDto;
import com.devflow.executor.dto.ExecuteResponseDto;
import com.devflow.executor.workspace.ExecutorWorkspaceManager;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing the controlled, narrow sandbox execution protocol.
 * Does NOT expose arbitrary Docker operations, commands, images, or mounts.
 */
@RestController
@RequestMapping("/api/v1/sandbox")
public class SandboxExecutorController {

    private static final Logger log = LoggerFactory.getLogger(SandboxExecutorController.class);

    private final ExecutorProperties properties;
    private final ExecutorWorkspaceManager workspaceManager;
    private final DockerSandboxExecutor dockerExecutor;

    public SandboxExecutorController(
            ExecutorProperties properties,
            ExecutorWorkspaceManager workspaceManager,
            DockerSandboxExecutor dockerExecutor
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.workspaceManager = Objects.requireNonNull(workspaceManager, "workspaceManager must not be null");
        this.dockerExecutor = Objects.requireNonNull(dockerExecutor, "dockerExecutor must not be null");
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody ExecuteRequestDto request) {
        if (request == null || request.executionId() == null || request.proposedChangeId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "executionId and proposedChangeId are required"
            ));
        }

        // Independent profile allowlist validation
        if (request.profile() == null || !properties.getAllowedProfiles().contains(request.profile())) {
            log.warn("Rejected execution request with unauthorized profile: {}", request.profile());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "Execution profile is forbidden or unknown: " + request.profile()
            ));
        }

        Path workspace = null;
        try {
            workspace = workspaceManager.prepareWorkspace(request.executionId(), request.files());
            ExecuteResponseDto response = dockerExecutor.run(
                    request.executionId(),
                    request.proposedChangeId(),
                    request.profile(),
                    workspace
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Workspace validation failed for [{}]: {}", request.executionId(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Execution failed for [{}]: {}", request.executionId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "EXECUTOR_ERROR",
                    "message", e.getMessage()
            ));
        } finally {
            workspaceManager.cleanupWorkspace(request.executionId());
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancel(@RequestBody CancelRequestDto request) {
        if (request != null && request.executionId() != null) {
            dockerExecutor.cancel(request.executionId());
            workspaceManager.cleanupWorkspace(request.executionId());
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
