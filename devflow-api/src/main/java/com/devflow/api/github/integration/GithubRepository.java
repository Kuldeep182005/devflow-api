package com.devflow.api.github.integration;

public record GithubRepository(
        String id,
        String owner,
        String fullName,
        String defaultBranch,
        Long installationId) {

    public GithubRepository(String id, String owner, String fullName, String defaultBranch) {
        this(id, owner, fullName, defaultBranch, null);
    }
}
