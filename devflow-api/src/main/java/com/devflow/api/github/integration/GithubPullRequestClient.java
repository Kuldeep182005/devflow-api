package com.devflow.api.github.integration;

import java.util.Optional;

public interface GithubPullRequestClient {

    PullRequestResult createPullRequest(
            String owner,
            String repo,
            String title,
            String body,
            String headBranch,
            String baseBranch,
            String installationToken);

    Optional<PullRequestResult> findPullRequest(
            String owner,
            String repo,
            String headBranch,
            String baseBranch,
            String installationToken);
}
