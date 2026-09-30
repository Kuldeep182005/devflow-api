package com.devflow.api.repository.service;

import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.github.integration.GithubIntegrationException;
import com.devflow.api.github.integration.GithubIntegrationService;
import com.devflow.api.github.integration.GithubRepository;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.repository.RepositoryRepository;
import com.devflow.api.repository.api.ConnectRepositoryRequest;
import com.devflow.api.repository.api.CreateRepositoryRequest;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.web.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final OrganizationMemberRepository memberRepository;
    private final GithubIntegrationService githubIntegrationService;

    public RepositoryService(
            RepositoryRepository repositoryRepository,
            OrganizationMemberRepository memberRepository) {
        this(repositoryRepository, memberRepository, null);
    }

    @Autowired
    public RepositoryService(
            RepositoryRepository repositoryRepository,
            OrganizationMemberRepository memberRepository,
            GithubIntegrationService githubIntegrationService) {
        this.repositoryRepository = repositoryRepository;
        this.memberRepository = memberRepository;
        this.githubIntegrationService = githubIntegrationService;
    }

    @Transactional
    public Repository create(UUID principalId, UUID organizationId, CreateRepositoryRequest request) {
        OrganizationMember member = requireMembership(principalId, organizationId);
        if (member.getRole() != OrganizationRole.OWNER && member.getRole() != OrganizationRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only owners and admins can register repositories");
        }
        String externalId = request.externalId().trim();
        if (repositoryRepository.findByOrganizationIdAndProviderAndExternalId(
                organizationId, request.provider(), externalId).isPresent()) {
            throw conflict("REPOSITORY_ALREADY_EXISTS", "The repository already exists in this organization");
        }

        Instant now = Instant.now();
        Repository repository = new Repository(
                member.getOrganization(),
                request.provider(),
                externalId,
                request.fullName().trim(),
                request.defaultBranch().trim(),
                true,
                now,
                now);
        try {
            return repositoryRepository.saveAndFlush(repository);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("REPOSITORY_ALREADY_EXISTS", "The repository already exists in this organization");
        }
    }

    @Transactional(readOnly = true)
    public Page<Repository> list(UUID principalId, UUID organizationId, Pageable pageable) {
        requireMembership(principalId, organizationId);
        return repositoryRepository.findAllByOrganizationId(organizationId, boundedPageable(pageable));
    }

    @Transactional(readOnly = true)
    public Repository get(UUID principalId, UUID organizationId, UUID repositoryId) {
        requireMembership(principalId, organizationId);
        return repositoryRepository.findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(() -> notFound("REPOSITORY_NOT_FOUND", "The repository does not exist"));
    }

    @Transactional
    public Repository connect(UUID principalId, UUID organizationId, ConnectRepositoryRequest request) {
        OrganizationMember member = requireMembership(principalId, organizationId);
        if (member.getRole() != OrganizationRole.OWNER && member.getRole() != OrganizationRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only owners and admins can connect repositories");
        }
        if (request == null || request.provider() != RepositoryProvider.GITHUB) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROVIDER",
                    "Only GitHub repositories can be connected");
        }

        String[] coordinates = coordinates(request);
        String owner = coordinates[0];
        String name = coordinates[1];
        if (!owner.equalsIgnoreCase(member.getOrganization().getSlug())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPOSITORY_ORGANIZATION_MISMATCH",
                    "The repository does not belong to this organization");
        }
        if (githubIntegrationService == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GITHUB_NOT_CONFIGURED",
                    "GitHub integration is not configured");
        }

        GithubRepository remote;
        try {
            remote = githubIntegrationService.verifyRepository(owner, name, request.installationId());
        } catch (GithubIntegrationException exception) {
            throw new ApiException(exception.getStatus(), exception.getCode(), exception.getMessage());
        }
        if (!remote.owner().equalsIgnoreCase(member.getOrganization().getSlug())
                || !remote.fullName().equalsIgnoreCase(owner + "/" + name)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPOSITORY_ORGANIZATION_MISMATCH",
                    "The repository does not belong to this organization");
        }
        if (remote.id().length() > 255 || remote.fullName().length() > 255
                || remote.defaultBranch().length() > 255) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_INVALID_RESPONSE",
                    "GitHub returned invalid repository metadata");
        }

        return repositoryRepository.findByProviderAndExternalId(RepositoryProvider.GITHUB, remote.id())
                .map(existing -> {
                    if (!existing.getOrganization().getId().equals(organizationId)) {
                        throw new ApiException(HttpStatus.CONFLICT, "REPOSITORY_ALREADY_CONNECTED",
                                "The repository is already connected to another organization");
                    }
                    if (remote.installationId() == null || remote.installationId() <= 0) {
                        throw new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_INVALID_RESPONSE",
                                "GitHub returned incomplete installation metadata");
                    }
                    existing.associateGithubInstallation(remote.installationId());
                    repositoryRepository.saveAndFlush(existing);
                    return existing;
                })
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    Repository repository = new Repository(
                            member.getOrganization(), RepositoryProvider.GITHUB, remote.id(),
                            remote.fullName(), remote.defaultBranch(), true, now, now);
                    if (remote.installationId() == null || remote.installationId() <= 0) {
                        throw new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_INVALID_RESPONSE",
                                "GitHub returned incomplete installation metadata");
                    }
                    repository.associateGithubInstallation(remote.installationId());
                    try {
                        return repositoryRepository.saveAndFlush(repository);
                    } catch (DataIntegrityViolationException exception) {
                        return repositoryRepository
                                .findByOrganizationIdAndProviderAndExternalId(
                                        organizationId, RepositoryProvider.GITHUB, remote.id())
                                .orElseThrow(() -> conflict("REPOSITORY_ALREADY_EXISTS",
                                        "The repository already exists in this organization"));
                    }
                });
    }

    private String[] coordinates(ConnectRepositoryRequest request) {
        String owner = request.owner();
        String name = request.name();
        String suppliedFullName = request.repository() != null
                ? request.repository() : request.externalRepository();
        if ((owner == null || owner.isBlank()) && suppliedFullName != null) {
            String fullName = suppliedFullName.trim();
            int separator = fullName.indexOf('/');
            if (separator > 0 && separator == fullName.lastIndexOf('/') && separator < fullName.length() - 1) {
                owner = fullName.substring(0, separator);
                name = fullName.substring(separator + 1);
            }
        }
        if (owner == null || name == null || owner.isBlank() || name.isBlank()
                || !owner.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
                || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPOSITORY",
                    "The repository identifier is invalid");
        }
        return new String[] {owner.trim(), name.trim()};
    }

    private OrganizationMember requireMembership(UUID principalId, UUID organizationId) {
        if (principalId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
        }
        return memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, principalId)
                .orElseThrow(() -> notFound("ORGANIZATION_NOT_FOUND", "The organization does not exist"));
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private Pageable boundedPageable(Pageable pageable) {
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 100),
                pageable.getSort());
    }
}
