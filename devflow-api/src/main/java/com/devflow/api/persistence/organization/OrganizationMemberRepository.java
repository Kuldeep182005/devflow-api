package com.devflow.api.persistence.organization;

import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationMemberId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, OrganizationMemberId> {

    boolean existsByIdUserIdAndIdOrganizationId(UUID userId, UUID organizationId);

    @EntityGraph(attributePaths = {"organization", "user"})
    Optional<OrganizationMember> findByIdOrganizationIdAndIdUserId(UUID organizationId, UUID userId);

    @Query("""
            select m from OrganizationMember m
            join fetch m.organization
            join fetch m.user
            where m.id.organizationId = :organizationId
            """)
    Page<OrganizationMember> findAllByOrganizationId(
            @Param("organizationId") UUID organizationId, Pageable pageable);

    long countByIdOrganizationIdAndRole(UUID organizationId, com.devflow.api.organization.domain.OrganizationRole role);
}
