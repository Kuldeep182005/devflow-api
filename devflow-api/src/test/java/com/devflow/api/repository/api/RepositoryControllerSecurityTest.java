package com.devflow.api.repository.api;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.repository.service.RepositoryService;
import com.devflow.api.security.AuthenticatedUser;
import com.devflow.api.security.JwtService;
import com.devflow.api.security.SecurityConfig;
import com.devflow.api.web.ApiException;
import com.devflow.api.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RepositoryController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RepositoryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepositoryService repositoryService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    private static final String CONNECT_PAYLOAD_MISMATCH = """
            {
                "provider": "GITHUB",
                "owner": "Kuldeep182005",
                "name": "GRADIO"
            }
            """;

    private static final String CONNECT_PAYLOAD_MATCHING = """
            {
                "provider": "GITHUB",
                "owner": "kuldeep182005",
                "name": "GRADIO"
            }
            """;

    @Test
    void unauthenticatedUserCannotConnectRepository() throws Exception {
        UUID organizationId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/repositories/connect", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECT_PAYLOAD_MISMATCH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void connect_whenOwnerDoesNotMatchOrganization_returnsForbiddenMismatchWithoutSensitiveData() throws Exception {
        UUID organizationId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "owner@example.com");

        when(repositoryService.connect(eq(user.id()), eq(organizationId), any(ConnectRepositoryRequest.class)))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "REPOSITORY_ORGANIZATION_MISMATCH",
                        "The repository does not belong to this organization"));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/repositories/connect", organizationId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECT_PAYLOAD_MISMATCH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPOSITORY_ORGANIZATION_MISMATCH"))
                .andExpect(jsonPath("$.message").value("The repository does not belong to this organization"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.privateKey").doesNotExist());
    }

    @Test
    void connect_whenGithubAppNotInstalled_returnsSafeBadRequest() throws Exception {
        UUID organizationId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "owner@example.com");

        when(repositoryService.connect(eq(user.id()), eq(organizationId), any(ConnectRepositoryRequest.class)))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "GITHUB_INSTALLATION_NOT_FOUND",
                        "The GitHub App is not installed for this organization"));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/repositories/connect", organizationId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECT_PAYLOAD_MISMATCH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GITHUB_INSTALLATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The GitHub App is not installed for this organization"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void connect_whenGithubNotConfigured_returnsSafeServiceUnavailable() throws Exception {
        UUID organizationId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "owner@example.com");

        when(repositoryService.connect(eq(user.id()), eq(organizationId), any(ConnectRepositoryRequest.class)))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GITHUB_NOT_CONFIGURED",
                        "GitHub integration is not configured"));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/repositories/connect", organizationId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECT_PAYLOAD_MATCHING))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GITHUB_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.message").value("GitHub integration is not configured"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void connect_whenNonOwnerNonAdminCaller_returnsForbidden() throws Exception {
        UUID organizationId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "member@example.com");

        when(repositoryService.connect(eq(user.id()), eq(organizationId), any(ConnectRepositoryRequest.class)))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Only owners and admins can connect repositories"));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/repositories/connect", organizationId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECT_PAYLOAD_MATCHING))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void connect_whenRepositoryBelongsToAnotherOwner_returnsConflict() throws Exception {
        UUID organizationId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "owner@example.com");

        when(repositoryService.connect(eq(user.id()), eq(organizationId), any(ConnectRepositoryRequest.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "REPOSITORY_ALREADY_CONNECTED",
                        "The repository is already connected to another organization"));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/repositories/connect", organizationId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECT_PAYLOAD_MATCHING))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPOSITORY_ALREADY_CONNECTED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void connect_whenSuccessful_returnsCreatedWithRepositoryResponse() throws Exception {
        UUID organizationId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "owner@example.com");

        Organization organization = new Organization("Kuldeep", "kuldeep182005", Instant.now(), Instant.now());
        Repository repository = new Repository(organization, RepositoryProvider.GITHUB, "12345",
                "kuldeep182005/GRADIO", "main", true, Instant.now(), Instant.now());

        when(repositoryService.connect(eq(user.id()), eq(organizationId), any(ConnectRepositoryRequest.class)))
                .thenReturn(repository);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/repositories/connect", organizationId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECT_PAYLOAD_MATCHING))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("kuldeep182005/GRADIO"))
                .andExpect(jsonPath("$.externalId").value("12345"))
                .andExpect(jsonPath("$.provider").value("GITHUB"))
                .andExpect(jsonPath("$.active").value(true));
    }
}
