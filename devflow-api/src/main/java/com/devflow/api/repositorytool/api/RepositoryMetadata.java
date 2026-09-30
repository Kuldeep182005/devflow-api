package com.devflow.api.repositorytool.api;

import java.util.UUID;

public record RepositoryMetadata(
        UUID repositoryId,
        String repositoryName,
        String provider,
        String defaultBranch,
        String configuredBranch) {
}
