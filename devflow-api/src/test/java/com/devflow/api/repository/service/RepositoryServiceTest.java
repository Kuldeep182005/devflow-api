package com.devflow.api.repository.service;

import com.devflow.api.github.integration.GithubIntegrationException;
import com.devflow.api.github.integration.GithubIntegrationService;
import com.devflow.api.github.integration.GithubRepository;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.repository.RepositoryRepository;
import com.devflow.api.repository.api.ConnectRepositoryRequest;
import com.devflow.api.repository.api.CreateRepositoryRequest;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.web.ApiException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryServiceTest {

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private GithubIntegrationService githubIntegrationService;

    private RepositoryService service;

    @BeforeEach
    void setUp() {
        service = new RepositoryService(repositoryRepository, memberRepository, githubIntegrationService);
    }

    @Test
    void doesNotExposeRepositoryToNonMember() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId, organizationId, UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(404);
                    assertThat(exception.getCode()).isEqualTo("ORGANIZATION_NOT_FOUND");
                });
    }

    @Test
    void createsRepositoryOnlyForMemberAndActivatesIt() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("DevFlow", "devflow", Instant.now(), Instant.now());
        OrganizationMember member = org.mockito.Mockito.mock(OrganizationMember.class);
        when(member.getOrganization()).thenReturn(organization);
        when(member.getRole()).thenReturn(OrganizationRole.ADMIN);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));
        when(repositoryRepository.findByOrganizationIdAndProviderAndExternalId(
                organizationId, RepositoryProvider.GITHUB, "42")).thenReturn(Optional.empty());
        when(repositoryRepository.saveAndFlush(any(Repository.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var repository = service.create(userId, organizationId,
                new CreateRepositoryRequest(RepositoryProvider.GITHUB, " 42 ", " acme/api ", " main "));

        assertThat(repository.getExternalId()).isEqualTo("42");
        assertThat(repository.getFullName()).isEqualTo("acme/api");
        assertThat(repository.getDefaultBranch()).isEqualTo("main");
        assertThat(repository.isActive()).isTrue();
    }

    @Test
    void connect_whenOwnerMatchesAndValidInstallation_succeeds() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("Kuldeep", "kuldeep182005", Instant.now(), Instant.now());
        OrganizationMember member = org.mockito.Mockito.mock(OrganizationMember.class);
        when(member.getOrganization()).thenReturn(organization);
        when(member.getRole()).thenReturn(OrganizationRole.OWNER);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));

        GithubRepository remote = new GithubRepository("123456", "Kuldeep182005", "Kuldeep182005/GRADIO", "main", 98765L);
        when(githubIntegrationService.verifyRepository("Kuldeep182005", "GRADIO", null))
                .thenReturn(remote);
        when(repositoryRepository.findByProviderAndExternalId(RepositoryProvider.GITHUB, "123456"))
                .thenReturn(Optional.empty());
        when(repositoryRepository.saveAndFlush(any(Repository.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConnectRepositoryRequest request = new ConnectRepositoryRequest(RepositoryProvider.GITHUB, "Kuldeep182005", "GRADIO");
        Repository repository = service.connect(userId, organizationId, request);

        assertThat(repository.getExternalId()).isEqualTo("123456");
        assertThat(repository.getFullName()).isEqualTo("Kuldeep182005/GRADIO");
        assertThat(repository.getGithubInstallationId()).isEqualTo(98765L);
        assertThat(repository.isActive()).isTrue();
    }

    @Test
    void connect_whenOwnerDoesNotMatchOrganizationSlug_throwsForbiddenMismatch() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("Dev Team", "dev", Instant.now(), Instant.now());
        OrganizationMember member = org.mockito.Mockito.mock(OrganizationMember.class);
        when(member.getOrganization()).thenReturn(organization);
        when(member.getRole()).thenReturn(OrganizationRole.OWNER);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));

        ConnectRepositoryRequest request = new ConnectRepositoryRequest(RepositoryProvider.GITHUB, "Kuldeep182005", "GRADIO");

        assertThatThrownBy(() -> service.connect(userId, organizationId, request))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("REPOSITORY_ORGANIZATION_MISMATCH");
                    assertThat(exception.getMessage()).contains("The repository does not belong to this organization");
                });
    }

    @Test
    void connect_whenCallerNotOwnerOrAdmin_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        OrganizationMember member = org.mockito.Mockito.mock(OrganizationMember.class);
        when(member.getRole()).thenReturn(OrganizationRole.MEMBER);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));

        ConnectRepositoryRequest request = new ConnectRepositoryRequest(RepositoryProvider.GITHUB, "acme", "api");

        assertThatThrownBy(() -> service.connect(userId, organizationId, request))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("FORBIDDEN");
                    assertThat(exception.getMessage()).contains("Only owners and admins can connect repositories");
                });
    }

    @Test
    void connect_whenCallerNotMember_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.empty());

        ConnectRepositoryRequest request = new ConnectRepositoryRequest(RepositoryProvider.GITHUB, "acme", "api");

        assertThatThrownBy(() -> service.connect(userId, organizationId, request))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("ORGANIZATION_NOT_FOUND");
                });
    }

    @Test
    void connect_whenProviderNotGithub_throwsBadRequest() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        OrganizationMember member = org.mockito.Mockito.mock(OrganizationMember.class);
        when(member.getRole()).thenReturn(OrganizationRole.ADMIN);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));

        ConnectRepositoryRequest request = new ConnectRepositoryRequest(null, "acme", "api");

        assertThatThrownBy(() -> service.connect(userId, organizationId, request))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_PROVIDER");
                });
    }

    @Test
    void connect_whenGithubAppNotInstalled_throwsBadRequestInstallationNotFound() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("Kuldeep", "kuldeep182005", Instant.now(), Instant.now());
        OrganizationMember member = org.mockito.Mockito.mock(OrganizationMember.class);
        when(member.getOrganization()).thenReturn(organization);
        when(member.getRole()).thenReturn(OrganizationRole.OWNER);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));

        when(githubIntegrationService.verifyRepository("Kuldeep182005", "GRADIO", null))
                .thenThrow(new GithubIntegrationException(
                        HttpStatus.BAD_REQUEST, "GITHUB_INSTALLATION_NOT_FOUND",
                        "The GitHub App is not installed for this organization"));

        ConnectRepositoryRequest request = new ConnectRepositoryRequest(RepositoryProvider.GITHUB, "Kuldeep182005", "GRADIO");

        assertThatThrownBy(() -> service.connect(userId, organizationId, request))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("GITHUB_INSTALLATION_NOT_FOUND");
                    assertThat(exception.getMessage()).contains("The GitHub App is not installed");
                });
    }

    @Test
    void connect_whenInstallationForbidden_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("Kuldeep", "kuldeep182005", Instant.now(), Instant.now());
        OrganizationMember member = org.mockito.Mockito.mock(OrganizationMember.class);
        when(member.getOrganization()).thenReturn(organization);
        when(member.getRole()).thenReturn(OrganizationRole.OWNER);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));

        when(githubIntegrationService.verifyRepository("Kuldeep182005", "GRADIO", 444L))
                .thenThrow(new GithubIntegrationException(
                        HttpStatus.FORBIDDEN, "GITHUB_INSTALLATION_FORBIDDEN",
                        "The GitHub installation is not authorized for this organization"));

        ConnectRepositoryRequest request = new ConnectRepositoryRequest(RepositoryProvider.GITHUB, "Kuldeep182005", "GRADIO", 444L);

        assertThatThrownBy(() -> service.connect(userId, organizationId, request))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("GITHUB_INSTALLATION_FORBIDDEN");
                });
    }

    @Test
    void connect_whenGithubApiFails_throwsSafeError() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("Kuldeep", "kuldeep182005", Instant.now(), Instant.now());
        OrganizationMember member = org.mockito.Mockito.mock(OrganizationMember.class);
        when(member.getOrganization()).thenReturn(organization);
        when(member.getRole()).thenReturn(OrganizationRole.OWNER);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));

        when(githubIntegrationService.verifyRepository("Kuldeep182005", "GRADIO", null))
                .thenThrow(new GithubIntegrationException(
                        HttpStatus.NOT_FOUND, "GITHUB_REPOSITORY_NOT_FOUND",
                        "The GitHub resource was not found"));

        ConnectRepositoryRequest request = new ConnectRepositoryRequest(RepositoryProvider.GITHUB, "Kuldeep182005", "GRADIO");

        assertThatThrownBy(() -> service.connect(userId, organizationId, request))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("GITHUB_REPOSITORY_NOT_FOUND");
                });
    }

    @Test
    void connect_whenRepositoryAlreadyConnectedToAnotherOrg_throwsConflict() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();
        Organization organization = new Organization("Kuldeep", "kuldeep182005", Instant.now(), Instant.now());
        Organization otherOrg = new Organization("Other", "other", Instant.now(), Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(otherOrg, "id", otherOrgId);

        OrganizationMember member = org.mockito.Mockito.mock(OrganizationMember.class);
        when(member.getOrganization()).thenReturn(organization);
        when(member.getRole()).thenReturn(OrganizationRole.OWNER);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(member));

        GithubRepository remote = new GithubRepository("123456", "Kuldeep182005", "Kuldeep182005/GRADIO", "main", 98765L);
        when(githubIntegrationService.verifyRepository("Kuldeep182005", "GRADIO", null))
                .thenReturn(remote);

        Repository existingOther = new Repository(otherOrg, RepositoryProvider.GITHUB, "123456", "Kuldeep182005/GRADIO", "main", true, Instant.now(), Instant.now());
        when(repositoryRepository.findByProviderAndExternalId(RepositoryProvider.GITHUB, "123456"))
                .thenReturn(Optional.of(existingOther));

        ConnectRepositoryRequest request = new ConnectRepositoryRequest(RepositoryProvider.GITHUB, "Kuldeep182005", "GRADIO");

        assertThatThrownBy(() -> service.connect(userId, organizationId, request))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("REPOSITORY_ALREADY_CONNECTED");
                    assertThat(exception.getMessage()).contains("The repository is already connected to another organization");
                });
    }
}
