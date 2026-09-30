package com.devflow.api.sandbox.executor;

import com.devflow.api.sandbox.config.SandboxProperties;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Communicates with the separate, isolated sandbox executor process over an
 * authenticated REST interface. The DevFlow API process does NOT possess direct
 * Docker daemon or socket access.
 */
@Component
public class RemoteSandboxExecutor implements SandboxExecutor {

    private static final Logger log = LoggerFactory.getLogger(RemoteSandboxExecutor.class);
    private static final String AUTH_HEADER = "X-Sandbox-Token";

    private final SandboxProperties properties;
    private final RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public RemoteSandboxExecutor(SandboxProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }

    public RemoteSandboxExecutor(SandboxProperties properties, RestTemplate restTemplate) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate must not be null");
    }

    @Override
    public SandboxExecutionResult execute(SandboxExecutionRequest request, Path workspaceDirectory) {
        Objects.requireNonNull(request, "request must not be null");

        List<ExecutionFilePayload> files = request.files();
        if ((files == null || files.isEmpty()) && workspaceDirectory != null && Files.isDirectory(workspaceDirectory)) {
            files = extractWorkspaceFiles(workspaceDirectory);
        }

        ExecuteRequestPayload payload = new ExecuteRequestPayload(
                request.executionId(),
                request.proposedChangeId(),
                request.profile().name(),
                files
        );

        HttpHeaders headers = createAuthHeaders();
        HttpEntity<ExecuteRequestPayload> entity = new HttpEntity<>(payload, headers);

        String url = properties.getExecutorUrl() + "/api/v1/sandbox/execute";
        try {
            ResponseEntity<ExecuteResponsePayload> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ExecuteResponsePayload.class
            );

            ExecuteResponsePayload body = response.getBody();
            if (body == null) {
                return SandboxExecutionResult.executorUnavailable(
                        request.executionId(),
                        request.proposedChangeId(),
                        "Empty response received from sandbox executor"
                );
            }

            SandboxExecutionStatus status = parseStatus(body.status());
            Duration duration = body.durationMs() != null ? Duration.ofMillis(body.durationMs()) : Duration.ZERO;

            return new SandboxExecutionResult(
                    request.executionId(),
                    request.proposedChangeId(),
                    status,
                    body.exitCode(),
                    duration,
                    body.stdout(),
                    body.stderr(),
                    Boolean.TRUE.equals(body.outputTruncated()),
                    body.failureCode(),
                    body.failureMessage()
            );

        } catch (HttpStatusCodeException ex) {
            log.warn("Sandbox executor rejected request [{}]: HTTP {}", request.executionId(), ex.getStatusCode());
            return SandboxExecutionResult.executorUnavailable(
                    request.executionId(),
                    request.proposedChangeId(),
                    "Sandbox executor error: " + ex.getStatusCode()
            );
        } catch (ResourceAccessException ex) {
            log.error("Failed to connect to sandbox executor at {}: {}", url, ex.getMessage());
            return SandboxExecutionResult.executorUnavailable(
                    request.executionId(),
                    request.proposedChangeId(),
                    "Sandbox executor is unreachable"
            );
        } catch (Exception ex) {
            log.error("Unexpected error communicating with sandbox executor: {}", ex.getMessage(), ex);
            return SandboxExecutionResult.executorUnavailable(
                    request.executionId(),
                    request.proposedChangeId(),
                    "Sandbox executor execution failure: " + ex.getMessage()
            );
        }
    }

    @Override
    public void cancel(UUID executionId) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        String url = properties.getExecutorUrl() + "/api/v1/sandbox/cancel";
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<CancelRequestPayload> entity = new HttpEntity<>(new CancelRequestPayload(executionId), headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
            log.info("Sent cancellation request for sandbox execution {}", executionId);
        } catch (Exception ex) {
            log.warn("Failed to notify sandbox executor of cancellation for {}: {}", executionId, ex.getMessage());
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(AUTH_HEADER, properties.getExecutorToken());
        return headers;
    }

    private List<ExecutionFilePayload> extractWorkspaceFiles(Path workspaceDirectory) {
        List<ExecutionFilePayload> list = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(workspaceDirectory)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    String relative = workspaceDirectory.relativize(file).toString().replace('\\', '/');
                    String content = Files.readString(file);
                    list.add(new ExecutionFilePayload(relative, "ADD", content, null));
                } catch (IOException e) {
                    log.debug("Could not read workspace file {}: {}", file, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.debug("Could not walk workspace directory: {}", e.getMessage());
        }
        return list;
    }

    private SandboxExecutionStatus parseStatus(String statusStr) {
        if (statusStr == null) {
            return SandboxExecutionStatus.EXECUTOR_UNAVAILABLE;
        }
        try {
            return SandboxExecutionStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return SandboxExecutionStatus.EXECUTOR_UNAVAILABLE;
        }
    }

    public record ExecuteRequestPayload(
            UUID executionId,
            UUID proposedChangeId,
            String profile,
            List<ExecutionFilePayload> files
    ) {}

    public record ExecuteResponsePayload(
            UUID executionId,
            UUID proposedChangeId,
            String status,
            Integer exitCode,
            Long durationMs,
            String stdout,
            String stderr,
            Boolean outputTruncated,
            String failureCode,
            String failureMessage
    ) {}

    public record CancelRequestPayload(
            UUID executionId
    ) {}
}
