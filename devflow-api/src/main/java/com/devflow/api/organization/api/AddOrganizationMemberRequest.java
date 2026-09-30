package com.devflow.api.organization.api;

import com.devflow.api.organization.domain.OrganizationRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddOrganizationMemberRequest(
        @NotNull UUID userId,
        @NotNull OrganizationRole role) {
}
