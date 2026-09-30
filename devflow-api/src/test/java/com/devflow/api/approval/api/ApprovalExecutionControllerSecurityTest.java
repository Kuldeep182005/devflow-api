package com.devflow.api.approval.api;

import com.devflow.api.approval.service.ApprovalService;
import com.devflow.api.github.mutation.api.GithubMutationResponse;
import com.devflow.api.github.mutation.exception.GithubMutationExceptions;
import com.devflow.api.github.mutation.service.GithubMutationService;
import com.devflow.api.security.AuthenticatedUser;
import com.devflow.api.security.JwtService;
import com.devflow.api.security.SecurityConfig;
import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApprovalController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ApprovalExecutionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApprovalService approvalService;

    @MockBean
    private GithubMutationService githubMutationService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    private UsernamePasswordAuthenticationToken auth(UUID userId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, "admin@example.com");
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    @DisplayName("Execute approved proposal returns 401 when unauthenticated")
    void executeRequiresAuthentication() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/approvals/" + approvalId + "/execute"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Execute returns 403 Forbidden when caller has insufficient role")
    void executeReturnsForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        when(githubMutationService.enqueueExecution(userId, orgId, approvalId))
                .thenThrow(GithubMutationExceptions.forbidden());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/approvals/" + approvalId + "/execute")
                        .with(authentication(auth(userId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Execute returns 404 when approval or organization is not found")
    void executeReturnsNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        when(githubMutationService.enqueueExecution(userId, orgId, approvalId))
                .thenThrow(GithubMutationExceptions.approvalNotFound());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/approvals/" + approvalId + "/execute")
                        .with(authentication(auth(userId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_FOUND"));
    }

    @Test
    @DisplayName("Execute returns 409 Conflict when approval is not in APPROVED state")
    void executeReturnsConflictForUnapproved() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        when(githubMutationService.enqueueExecution(userId, orgId, approvalId))
                .thenThrow(GithubMutationExceptions.approvalNotApproved());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/approvals/" + approvalId + "/execute")
                        .with(authentication(auth(userId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_APPROVED"));
    }

    @Test
    @DisplayName("Execute returns 202 Accepted when successfully enqueued")
    void executeSucceedsWith202Accepted() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        UUID mutationId = UUID.randomUUID();

        GithubMutationResponse response = new GithubMutationResponse(
                mutationId, orgId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0L,
                approvalId, UUID.randomUUID(), "base-sha", "devflow/task-1/change-1", null,
                null, null, "QUEUED", null, null, Instant.now(), Instant.now(), 0L
        );

        when(githubMutationService.enqueueExecution(userId, orgId, approvalId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/approvals/" + approvalId + "/execute")
                        .with(authentication(auth(userId))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(mutationId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.branchName").value("devflow/task-1/change-1"));
    }

    @Test
    @DisplayName("Get mutation returns 200 OK when found")
    void getMutationReturns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        UUID mutationId = UUID.randomUUID();

        GithubMutationResponse response = new GithubMutationResponse(
                mutationId, orgId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0L,
                approvalId, UUID.randomUUID(), "base-sha", "devflow/task-1/change-1", "commit-sha",
                42L, "https://github.com/octocat/hello-world/pull/42", "SUCCEEDED", null, null,
                Instant.now(), Instant.now(), 0L
        );

        when(githubMutationService.getMutationByApprovalId(userId, orgId, approvalId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/approvals/" + approvalId + "/mutation")
                        .with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mutationId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.pullRequestNumber").value(42))
                .andExpect(jsonPath("$.pullRequestUrl").value("https://github.com/octocat/hello-world/pull/42"));
    }
}
