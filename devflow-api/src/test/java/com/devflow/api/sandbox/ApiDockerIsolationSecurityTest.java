package com.devflow.api.sandbox;

import com.devflow.api.sandbox.config.SandboxProperties;
import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.sandbox.executor.ExecutionFilePayload;
import com.devflow.api.sandbox.executor.RemoteSandboxExecutor;
import com.devflow.api.sandbox.executor.SandboxExecutionRequest;
import com.devflow.api.sandbox.executor.SandboxExecutionResult;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ApiDockerIsolationSecurityTest {

    private SandboxProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private RemoteSandboxExecutor executor;

    @BeforeEach
    void setUp() {
        properties = new SandboxProperties();
        properties.setExecutorUrl("http://sandbox-daemon:8089");
        properties.setExecutorToken("test-secret-token-xyz");

        restTemplate = new RestTemplateBuilder().build();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        executor = new RemoteSandboxExecutor(properties, restTemplate);
    }

    @Test
    @DisplayName("API production code must NOT contain DockerClient or Docker daemon references")
    void apiProductionCodeHasNoDockerClientReferences() {
        for (java.lang.reflect.Field field : RemoteSandboxExecutor.class.getDeclaredFields()) {
            assertThat(field.getType().getName().toLowerCase()).doesNotContain("docker");
        }
        for (java.lang.reflect.Method method : RemoteSandboxExecutor.class.getDeclaredMethods()) {
            assertThat(method.getReturnType().getName().toLowerCase()).doesNotContain("docker");
        }
    }

    @Test
    @DisplayName("devflow-api pom.xml must not declare docker-java dependencies")
    void pomDoesNotDeclareDockerJava() throws Exception {
        Path pomPath = Path.of("pom.xml");
        if (Files.exists(pomPath)) {
            String content = Files.readString(pomPath);
            assertThat(content).doesNotContain("docker-java");
        }
    }

    @Test
    @DisplayName("Remote executor attaches secret token and transmits only trusted payload")
    void remoteExecutorAttachesAuthTokenAndSendsTrustedPayload() {
        UUID executionId = UUID.randomUUID();
        UUID proposedChangeId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();

        mockServer.expect(requestTo("http://sandbox-daemon:8089/api/v1/sandbox/execute"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Sandbox-Token", "test-secret-token-xyz"))
                .andExpect(jsonPath("$.executionId").value(executionId.toString()))
                .andExpect(jsonPath("$.proposedChangeId").value(proposedChangeId.toString()))
                .andExpect(jsonPath("$.profile").value("JAVA_MAVEN_TEST"))
                .andExpect(jsonPath("$.files[0].filePath").value("src/test/java/AppTest.java"))
                .andRespond(withSuccess("""
                        {
                            "executionId": "%s",
                            "proposedChangeId": "%s",
                            "status": "SUCCEEDED",
                            "exitCode": 0,
                            "durationMs": 1500,
                            "stdout": "Tests run: 1, Failures: 0",
                            "stderr": "",
                            "outputTruncated": false
                        }
                        """.formatted(executionId, proposedChangeId), MediaType.APPLICATION_JSON));

        SandboxExecutionRequest request = new SandboxExecutionRequest(
                executionId,
                proposedChangeId,
                taskId,
                orgId,
                repoId,
                SandboxExecutionProfile.JAVA_MAVEN_TEST,
                List.of(new ExecutionFilePayload("src/test/java/AppTest.java", "ADD", "content", null))
        );

        SandboxExecutionResult result = executor.execute(request, null);

        mockServer.verify();
        assertThat(result.status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("Tests run: 1");
    }

    @Test
    @DisplayName("Remote executor handles unauthorized 401 response from sandbox daemon as EXECUTOR_UNAVAILABLE")
    void remoteExecutorHandlesUnauthorizedResponse() {
        UUID executionId = UUID.randomUUID();
        UUID proposedChangeId = UUID.randomUUID();

        mockServer.expect(requestTo("http://sandbox-daemon:8089/api/v1/sandbox/execute"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        SandboxExecutionRequest request = new SandboxExecutionRequest(
                executionId,
                proposedChangeId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                SandboxExecutionProfile.JAVA_MAVEN_TEST
        );

        SandboxExecutionResult result = executor.execute(request, null);

        mockServer.verify();
        assertThat(result.status()).isEqualTo(SandboxExecutionStatus.EXECUTOR_UNAVAILABLE);
        assertThat(result.failureMessage()).contains("401");
    }

    @Test
    @DisplayName("Remote executor handles network unreachable as EXECUTOR_UNAVAILABLE")
    void remoteExecutorHandlesNetworkFailure() {
        // Points to an unreachable host port with 100ms timeout
        properties.setExecutorUrl("http://127.0.0.1:59999");
        properties.setConnectTimeoutMs(100);
        RemoteSandboxExecutor unreachableExecutor = new RemoteSandboxExecutor(
                properties,
                new RestTemplateBuilder().connectTimeout(java.time.Duration.ofMillis(100)).readTimeout(java.time.Duration.ofMillis(100))
        );

        SandboxExecutionRequest request = new SandboxExecutionRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                SandboxExecutionProfile.JAVA_MAVEN_TEST
        );

        SandboxExecutionResult result = unreachableExecutor.execute(request, null);

        assertThat(result.status()).isEqualTo(SandboxExecutionStatus.EXECUTOR_UNAVAILABLE);
        assertThat(result.failureMessage()).contains("unreachable");
    }

    @Test
    @DisplayName("Remote executor cancellation endpoint sends cancel request with auth token")
    void cancelSendsAuthToken() {
        UUID executionId = UUID.randomUUID();

        mockServer.expect(requestTo("http://sandbox-daemon:8089/api/v1/sandbox/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Sandbox-Token", "test-secret-token-xyz"))
                .andExpect(jsonPath("$.executionId").value(executionId.toString()))
                .andRespond(withSuccess());

        executor.cancel(executionId);

        mockServer.verify();
    }
}
