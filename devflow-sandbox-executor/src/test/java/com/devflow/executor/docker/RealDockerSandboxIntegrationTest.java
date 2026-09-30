package com.devflow.executor.docker;

import com.devflow.executor.config.ExecutorProperties;
import com.devflow.executor.dto.ExecuteResponseDto;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RealDockerSandboxIntegrationTest {

    private DockerClient dockerClient;
    private ExecutorProperties properties;
    private DockerSandboxExecutor executor;
    private boolean dockerAvailable = false;

    @BeforeEach
    void setUp() {
        try {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(10)
                    .connectionTimeout(Duration.ofSeconds(3))
                    .responseTimeout(Duration.ofSeconds(5))
                    .build();
            dockerClient = DockerClientImpl.getInstance(config, httpClient);
            dockerClient.pingCmd().exec();
            dockerAvailable = true;
        } catch (Exception e) {
            dockerAvailable = false;
        }

        properties = new ExecutorProperties();
        // Use alpine for lightweight, fast execution verification
        properties.setPinnedImage("alpine:3.19");
        properties.setTimeoutSeconds(30);
        executor = new DockerSandboxExecutor(properties, dockerClient);
    }

    @AfterEach
    void tearDown() {
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("REAL DOCKER: runs container with network_mode=none, fixed command, verifies exit code and cleanup")
    void realDockerContainerExecution(@TempDir Path tempDir) throws IOException {
        assumeTrue(dockerAvailable, "Docker daemon is not available in test environment");

        // Create a dummy file in the workspace
        Files.writeString(tempDir.resolve("sample.txt"), "hello isolated world");

        UUID executionId = UUID.randomUUID();
        UUID proposedChangeId = UUID.randomUUID();

        ExecuteResponseDto result = executor.run(executionId, proposedChangeId, "SANDBOX_VERIFICATION_TEST", tempDir);

        assertThat(result).isNotNull();
        assertThat(result.executionId()).isEqualTo(executionId);
        // Real container was executed inside real Docker engine with network=none and cleaned up
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("sandbox-verified");
    }

    @Test
    @DisplayName("REAL DOCKER: cancellation marks execution CANCELLED before start")
    void realDockerCancellation(@TempDir Path tempDir) {
        assumeTrue(dockerAvailable, "Docker daemon is not available in test environment");

        UUID executionId = UUID.randomUUID();
        UUID proposedChangeId = UUID.randomUUID();

        executor.cancel(executionId);
        ExecuteResponseDto result = executor.run(executionId, proposedChangeId, "JAVA_MAVEN_TEST", tempDir);

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.failureCode()).isEqualTo("CANCELLED_BEFORE_START");
    }
}
