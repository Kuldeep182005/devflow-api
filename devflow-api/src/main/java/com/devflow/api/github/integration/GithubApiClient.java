package com.devflow.api.github.integration;

import java.util.OptionalLong;

/**
 * The only boundary through which application code talks to GitHub.
 */
public interface GithubApiClient {

    OptionalLong findInstallation(String account, String appJwt);

    String createInstallationToken(long installationId, String appJwt);

    GithubRepository getRepository(String owner, String repository, String installationToken);
}
