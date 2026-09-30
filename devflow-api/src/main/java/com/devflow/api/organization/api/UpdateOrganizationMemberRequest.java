package com.devflow.api.organization.api;

import com.devflow.api.organization.domain.OrganizationRole;
import jakarta.validation.constraints.NotNull;

public record UpdateOrganizationMemberRequest(@NotNull OrganizationRole role) {
}
