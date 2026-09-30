package com.devflow.api.repository.api;

import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import java.time.Instant;
import java.util.UUID;

public record RepositoryResponse(
        UUID id,
        UUID organizationId,
        RepositoryProvider provider,
        String externalId,
        String fullName,
        String defaultBranch,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static RepositoryResponse from(Repository repository) {
        return new RepositoryResponse(
                repository.getId(),
                repository.getOrganization().getId(),
                repository.getProvider(),
                repository.getExternalId(),
                repository.getFullName(),
                repository.getDefaultBranch(),
                repository.isActive(),
                repository.getCreatedAt(),
                repository.getUpdatedAt());
    }
}
