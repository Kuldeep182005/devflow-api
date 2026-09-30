package com.devflow.api.organization.api;

import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import java.time.Instant;
import java.util.UUID;

public record OrganizationMemberResponse(
        UUID organizationId,
        UUID userId,
        String email,
        String displayName,
        OrganizationRole role,
        Instant createdAt) {

    public static OrganizationMemberResponse from(OrganizationMember member) {
        return new OrganizationMemberResponse(
                member.getOrganization().getId(),
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getDisplayName(),
                member.getRole(),
                member.getCreatedAt());
    }
}
