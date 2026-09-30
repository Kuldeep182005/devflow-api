package com.devflow.api.approval.api;

import com.devflow.api.approval.domain.Approval;
import com.devflow.api.approval.domain.ApprovalStatus;
import com.devflow.api.approval.exception.ApprovalExceptions;
import com.devflow.api.approval.service.ApprovalService;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.security.AuthenticatedUser;
import com.devflow.api.security.JwtService;
import com.devflow.api.security.SecurityConfig;
import com.devflow.api.task.domain.Task;
import com.devflow.api.user.domain.User;
import com.devflow.api.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApprovalController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ApprovalControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApprovalService approvalService;

    @MockBean
    private com.devflow.api.github.mutation.service.GithubMutationService githubMutationService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    private UsernamePasswordAuthenticationToken auth(UUID userId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com");
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Approval createMockApproval(UUID orgId, UUID approvalId, ApprovalStatus status) {
        ProposedChange pc = org.mockito.Mockito.mock(ProposedChange.class);
        Task task = org.mockito.Mockito.mock(Task.class);
        Organization organization = org.mockito.Mockito.mock(Organization.class);
        Repository repo = org.mockito.Mockito.mock(Repository.class);
        SandboxExecution execution = org.mockito.Mockito.mock(SandboxExecution.class);
        User requester = org.mockito.Mockito.mock(User.class);
        User decider = org.mockito.Mockito.mock(User.class);

        UUID taskId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        UUID pcId = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();
        UUID decId = UUID.randomUUID();
        UUID execId = UUID.randomUUID();

        when(pc.getId()).thenReturn(pcId);
        when(task.getId()).thenReturn(taskId);
        when(organization.getId()).thenReturn(orgId);
        when(repo.getId()).thenReturn(repoId);
        when(execution.getId()).thenReturn(execId);
        when(execution.getStatus()).thenReturn(SandboxExecutionStatus.SUCCEEDED);
        when(requester.getId()).thenReturn(reqId);
        when(decider.getId()).thenReturn(decId);

        Approval approval = new Approval(pc, task, organization, repo, 1L, execution, requester, Instant.now());
        approval.assignIdForTesting(approvalId);
        if (status == ApprovalStatus.APPROVED) {
            approval.approve(decider, "LGTM", Instant.now());
        } else if (status == ApprovalStatus.REJECTED) {
            approval.reject(decider, "Buggy", Instant.now());
        }
        return approval;
    }

    @Test
    @DisplayName("Unauthenticated request returns 401 UNAUTHORIZED")
    void unauthenticatedRequestReturns401() throws Exception {
        UUID orgId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/approvals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Request approval returns 201 CREATED for authorized member")
    void requestApprovalReturns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID pcId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        Approval mockApproval = createMockApproval(orgId, approvalId, ApprovalStatus.PENDING);
        when(approvalService.requestApproval(eq(userId), eq(orgId), any())).thenReturn(mockApproval);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/approvals")
                        .with(authentication(auth(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposedChangeId\":\"" + pcId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(approvalId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.proposedChangeVersion").value(1));
    }

    @Test
    @DisplayName("Request approval returns 400 when proposedChangeId is missing")
    void requestApprovalReturns400WhenMissingBody() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/approvals")
                        .with(authentication(auth(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Approve returns 403 FORBIDDEN when user has insufficient role")
    void approveReturns403WhenForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        when(approvalService.approve(eq(userId), eq(orgId), eq(approvalId), any()))
                .thenThrow(ApprovalExceptions.forbidden("Only organization owners and admins can approve or reject changes"));

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/approvals/" + approvalId + "/approve")
                        .with(authentication(auth(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"LGTM\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Approve returns 200 OK on success")
    void approveReturns200OnSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        Approval mockApproval = createMockApproval(orgId, approvalId, ApprovalStatus.APPROVED);
        when(approvalService.approve(eq(userId), eq(orgId), eq(approvalId), any()))
                .thenReturn(mockApproval);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/approvals/" + approvalId + "/approve")
                        .with(authentication(auth(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"LGTM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvalId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Reject returns 400 when reason is blank")
    void rejectReturns400WhenReasonBlank() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/approvals/" + approvalId + "/reject")
                        .with(authentication(auth(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Reject returns 200 OK on success")
    void rejectReturns200OnSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        Approval mockApproval = createMockApproval(orgId, approvalId, ApprovalStatus.REJECTED);
        when(approvalService.reject(eq(userId), eq(orgId), eq(approvalId), any()))
                .thenReturn(mockApproval);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/approvals/" + approvalId + "/reject")
                        .with(authentication(auth(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Security issue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvalId.toString()))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("Get approval returns 404 NOT_FOUND for cross-organization access")
    void getApprovalReturns404ForCrossOrg() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        when(approvalService.getApproval(eq(userId), eq(orgId), eq(approvalId)))
                .thenThrow(ApprovalExceptions.notFound());

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/approvals/" + approvalId)
                        .with(authentication(auth(userId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_FOUND"));
    }

    @Test
    @DisplayName("Malformed UUID parameter returns 400 VALIDATION_ERROR")
    void malformedUuidReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/approvals/not-a-valid-uuid")
                        .with(authentication(auth(userId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("List approvals returns paginated responses")
    void listApprovalsReturnsPage() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        Approval mockApproval = createMockApproval(orgId, approvalId, ApprovalStatus.PENDING);
        when(approvalService.listApprovals(eq(userId), eq(orgId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(mockApproval)));

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/approvals")
                        .with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(approvalId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
