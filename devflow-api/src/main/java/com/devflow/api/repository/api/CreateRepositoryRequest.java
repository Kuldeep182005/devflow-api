package com.devflow.api.repository.api;

import com.devflow.api.repository.domain.RepositoryProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRepositoryRequest(
        @NotNull RepositoryProvider provider,
        @NotBlank @Size(max = 255) String externalId,
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 255) String defaultBranch) {
}
