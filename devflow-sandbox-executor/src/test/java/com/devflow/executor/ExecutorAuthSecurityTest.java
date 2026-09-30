package com.devflow.executor;

import com.devflow.executor.config.ExecutorProperties;
import com.devflow.executor.docker.DockerSandboxExecutor;
import com.devflow.executor.dto.ExecuteResponseDto;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExecutorAuthSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExecutorProperties properties;

    @MockBean
    private DockerSandboxExecutor dockerSandboxExecutor;

    @Test
    @DisplayName("Health endpoint is accessible without authentication")
    void healthAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/sandbox/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Execution endpoint rejects request without authentication token")
    void executeRejectsMissingToken() throws Exception {
        mockMvc.perform(post("/api/v1/sandbox/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Execution endpoint rejects request with invalid authentication token")
    void executeRejectsInvalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/sandbox/execute")
                        .header("X-Sandbox-Token", "wrong-secret-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Rejects request with forbidden/unknown execution profile")
    void executeRejectsUnknownProfile() throws Exception {
        UUID executionId = UUID.randomUUID();
        UUID proposedChangeId = UUID.randomUUID();

        String payload = """
                {
                    "executionId": "%s",
                    "proposedChangeId": "%s",
                    "profile": "CUSTOM_PYTHON_SHELL",
                    "files": []
                }
                """.formatted(executionId, proposedChangeId);

        mockMvc.perform(post("/api/v1/sandbox/execute")
                        .header("X-Sandbox-Token", properties.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("forbidden or unknown")));
    }

    @Test
    @DisplayName("Rejects request with path traversal in files payload")
    void executeRejectsPathTraversal() throws Exception {
        UUID executionId = UUID.randomUUID();
        UUID proposedChangeId = UUID.randomUUID();

        String payload = """
                {
                    "executionId": "%s",
                    "proposedChangeId": "%s",
                    "profile": "JAVA_MAVEN_TEST",
                    "files": [
                        {
                            "filePath": "../../etc/shadow",
                            "operation": "ADD",
                            "content": "malicious"
                        }
                    ]
                }
                """.formatted(executionId, proposedChangeId);

        mockMvc.perform(post("/api/v1/sandbox/execute")
                        .header("X-Sandbox-Token", properties.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Path traversal")));
    }

    @Test
    @DisplayName("Authenticated valid request delegates to DockerSandboxExecutor")
    void executeAcceptsValidRequest() throws Exception {
        UUID executionId = UUID.randomUUID();
        UUID proposedChangeId = UUID.randomUUID();

        when(dockerSandboxExecutor.run(eq(executionId), eq(proposedChangeId), eq("JAVA_MAVEN_TEST"), any()))
                .thenReturn(new ExecuteResponseDto(executionId, proposedChangeId, "SUCCEEDED", 0, 1200L, "Tests run: 1", "", false, null, null));

        String payload = """
                {
                    "executionId": "%s",
                    "proposedChangeId": "%s",
                    "profile": "JAVA_MAVEN_TEST",
                    "files": [
                        {
                            "filePath": "src/test/java/Test.java",
                            "operation": "ADD",
                            "content": "class Test {}"
                        }
                    ]
                }
                """.formatted(executionId, proposedChangeId);

        mockMvc.perform(post("/api/v1/sandbox/execute")
                        .header("X-Sandbox-Token", properties.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.exitCode").value(0));
    }
}
