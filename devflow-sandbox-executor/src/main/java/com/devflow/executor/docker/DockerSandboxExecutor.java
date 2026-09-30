package com.devflow.executor.docker;

import com.devflow.executor.config.ExecutorProperties;
import com.devflow.executor.dto.ExecuteResponseDto;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Executes sandboxed tasks inside isolated Docker containers.
 * This class resides strictly inside the isolated sandbox executor daemon,
 * ensuring the DevFlow API application never has Docker daemon or socket access.
 */
@Component
public class DockerSandboxExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxExecutor.class);

    private final ExecutorProperties properties;
    private final DockerClient dockerClient;
    private final Map<UUID, String> activeContainers = new ConcurrentHashMap<>();
    private final Set<UUID> cancelledExecutions = ConcurrentHashMap.newKeySet();

    @Autowired
    public DockerSandboxExecutor(ExecutorProperties properties) {
        this(properties, createDefaultDockerClient());
    }

    public DockerSandboxExecutor(ExecutorProperties properties, DockerClient dockerClient) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.dockerClient = dockerClient;
    }

    private static DockerClient createDefaultDockerClient() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(50)
                .connectionTimeout(Duration.ofSeconds(15))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    public ExecuteResponseDto run(UUID executionId, UUID proposedChangeId, String profile, Path workspaceDirectory) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(workspaceDirectory, "workspaceDirectory must not be null");

        if (cancelledExecutions.remove(executionId)) {
            return new ExecuteResponseDto(executionId, proposedChangeId, "CANCELLED", null, 0L, null, null, false, "CANCELLED_BEFORE_START", "Execution was cancelled before starting");
        }

        if (dockerClient == null) {
            return new ExecuteResponseDto(executionId, proposedChangeId, "EXECUTOR_UNAVAILABLE", null, 0L, null, null, false, "DOCKER_UNAVAILABLE", "Docker daemon connection unavailable");
        }

        List<String> command;
        String image;
        if ("SANDBOX_VERIFICATION_TEST".equals(profile)) {
            command = List.of("echo", "sandbox-verified");
            image = "alpine:3.19";
        } else {
            command = List.of("mvn", "test", "-B", "-DskipDocker=true");
            image = properties.getPinnedImage();
        }

        String containerId = null;
        Instant start = Instant.now();

        try {
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withNetworkMode("none")
                    .withCapDrop(Capability.ALL)
                    .withPrivileged(false)
                    .withReadonlyRootfs(true)
                    .withMemory(properties.getMemoryBytes())
                    .withCpuQuota(properties.getCpuQuota())
                    .withCpuPeriod(properties.getCpuPeriod())
                    .withPidsLimit(properties.getMaxProcesses())
                    .withBinds(new Bind(workspaceDirectory.toAbsolutePath().toString(), new Volume("/workspace"), AccessMode.rw));

            CreateContainerCmd createCmd = dockerClient.createContainerCmd(image)
                    .withCmd(command)
                    .withWorkingDir("/workspace")
                    .withHostConfig(hostConfig)
                    .withEnv("CI=true", "LANG=C.UTF-8")
                    .withUser("1000:1000")
                    .withLabels(Map.of(
                            "devflow.execution_id", executionId.toString(),
                            "devflow.managed", "true"
                    ));

            CreateContainerResponse containerResponse = createCmd.exec();
            containerId = containerResponse.getId();
            activeContainers.put(executionId, containerId);

            if (cancelledExecutions.remove(executionId)) {
                terminateContainer(containerId);
                return new ExecuteResponseDto(executionId, proposedChangeId, "CANCELLED", null, Duration.between(start, Instant.now()).toMillis(), null, null, false, "CANCELLED", "Execution cancelled");
            }

            dockerClient.startContainerCmd(containerId).exec();

            WaitContainerResultCallback waitCallback = new WaitContainerResultCallback();
            dockerClient.waitContainerCmd(containerId).exec(waitCallback);

            boolean finished = waitCallback.awaitCompletion(properties.getTimeoutSeconds(), TimeUnit.SECONDS);

            Duration duration = Duration.between(start, Instant.now());

            if (!finished) {
                terminateContainer(containerId);
                return new ExecuteResponseDto(executionId, proposedChangeId, "TIMED_OUT", null, duration.toMillis(), null, null, false, "TIMEOUT_EXCEEDED", "Container execution exceeded timeout");
            }

            if (cancelledExecutions.remove(executionId)) {
                return new ExecuteResponseDto(executionId, proposedChangeId, "CANCELLED", null, duration.toMillis(), null, null, false, "CANCELLED", "Execution was cancelled");
            }

            int exitCode = waitCallback.awaitStatusCode();

            OutputCollector outputCollector = collectBoundedOutput(containerId, properties.getMaxOutputCharacters());

            String status = (exitCode == 0) ? "SUCCEEDED" : "FAILED";
            return new ExecuteResponseDto(
                    executionId,
                    proposedChangeId,
                    status,
                    exitCode,
                    duration.toMillis(),
                    outputCollector.stdout.toString(),
                    outputCollector.stderr.toString(),
                    outputCollector.truncated.get(),
                    (exitCode != 0) ? "TEST_FAILURE" : null,
                    null
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminateContainer(containerId);
            return new ExecuteResponseDto(executionId, proposedChangeId, "CANCELLED", null, Duration.between(start, Instant.now()).toMillis(), null, null, false, "INTERRUPTED", "Execution thread interrupted");
        } catch (Exception e) {
            log.error("Container execution failed for [{}]: {}", executionId, e.getMessage(), e);
            terminateContainer(containerId);
            return new ExecuteResponseDto(executionId, proposedChangeId, "EXECUTOR_UNAVAILABLE", null, Duration.between(start, Instant.now()).toMillis(), null, null, false, "CONTAINER_EXECUTION_ERROR", "Container failed: " + e.getMessage());
        } finally {
            activeContainers.remove(executionId);
            cleanupContainer(containerId);
        }
    }

    public void cancel(UUID executionId) {
        if (executionId == null) {
            return;
        }
        cancelledExecutions.add(executionId);
        String containerId = activeContainers.remove(executionId);
        if (containerId != null) {
            terminateContainer(containerId);
            cleanupContainer(containerId);
        }
    }

    private void terminateContainer(String containerId) {
        if (containerId == null || dockerClient == null) {
            return;
        }
        try {
            dockerClient.killContainerCmd(containerId).exec();
        } catch (Exception e) {
            log.debug("Could not kill container {}: {}", containerId, e.getMessage());
        }
    }

    private void cleanupContainer(String containerId) {
        if (containerId == null || dockerClient == null) {
            return;
        }
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.debug("Could not remove container {}: {}", containerId, e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private OutputCollector collectBoundedOutput(String containerId, int maxChars) {
        OutputCollector collector = new OutputCollector(maxChars);
        try {
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(false)
                    .exec(new LogContainerResultCallback() {
                        @Override
                        public void onNext(Frame item) {
                            if (item == null || item.getPayload() == null) {
                                return;
                            }
                            String text = new String(item.getPayload(), StandardCharsets.UTF_8);
                            if (item.getStreamType() == StreamType.STDERR) {
                                collector.appendStderr(text);
                            } else {
                                collector.appendStdout(text);
                            }
                        }
                    }).awaitCompletion(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Error collecting container logs for {}: {}", containerId, e.getMessage());
        }
        return collector;
    }

    private static class OutputCollector {
        final int maxChars;
        final StringBuilder stdout = new StringBuilder();
        final StringBuilder stderr = new StringBuilder();
        final AtomicBoolean truncated = new AtomicBoolean(false);

        OutputCollector(int maxChars) {
            this.maxChars = maxChars;
        }

        synchronized void appendStdout(String text) {
            if (stdout.length() >= maxChars) {
                truncated.set(true);
                return;
            }
            int remaining = maxChars - stdout.length();
            if (text.length() > remaining) {
                stdout.append(text, 0, remaining);
                truncated.set(true);
            } else {
                stdout.append(text);
            }
        }

        synchronized void appendStderr(String text) {
            if (stderr.length() >= maxChars) {
                truncated.set(true);
                return;
            }
            int remaining = maxChars - stderr.length();
            if (text.length() > remaining) {
                stderr.append(text, 0, remaining);
                truncated.set(true);
            } else {
                stderr.append(text);
            }
        }
    }
}
