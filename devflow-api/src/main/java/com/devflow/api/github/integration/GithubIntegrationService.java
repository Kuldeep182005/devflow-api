package com.devflow.api.github.integration;

import java.util.OptionalLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GithubIntegrationService {

    private final GithubAppJwtService appJwtService;
    private final GithubApiClient githubApiClient;

    public GithubIntegrationService(
            GithubAppJwtService appJwtService,
            GithubApiClient githubApiClient) {
        this.appJwtService = appJwtService;
        this.githubApiClient = githubApiClient;
    }

    public GithubRepository verifyRepository(String account, String repository, Long requestedInstallationId) {
        String appJwt = appJwtService.generate();
        if (requestedInstallationId != null && requestedInstallationId <= 0) {
            throw new GithubIntegrationException(HttpStatus.BAD_REQUEST, "INVALID_GITHUB_INSTALLATION",
                    "The GitHub installation id is invalid");
        }
        long installationId = findInstallation(account, appJwt);
        if (requestedInstallationId != null && requestedInstallationId != installationId) {
            throw new GithubIntegrationException(HttpStatus.FORBIDDEN, "GITHUB_INSTALLATION_FORBIDDEN",
                    "The GitHub installation is not authorized for this organization");
        }
        if (installationId <= 0) {
            throw new GithubIntegrationException(HttpStatus.BAD_REQUEST, "GITHUB_INSTALLATION_NOT_FOUND",
                    "The GitHub App is not installed for this organization");
        }
        // The token is local to this call and is never logged, returned, or persisted.
        String installationToken = githubApiClient.createInstallationToken(installationId, appJwt);
        GithubRepository verified = githubApiClient.getRepository(account, repository, installationToken);
        return new GithubRepository(
                verified.id(), verified.owner(), verified.fullName(), verified.defaultBranch(), installationId);
    }

    public String getInstallationToken(long installationId) {
        if (installationId <= 0) {
            throw new GithubIntegrationException(HttpStatus.BAD_REQUEST, "INVALID_GITHUB_INSTALLATION",
                    "The GitHub installation id is invalid");
        }
        String appJwt = appJwtService.generate();
        return githubApiClient.createInstallationToken(installationId, appJwt);
    }

    private long findInstallation(String account, String appJwt) {
        OptionalLong installation = githubApiClient.findInstallation(account, appJwt);
        return installation.orElse(0L);
    }
}
