package com.devflow.api.persistence.organization;

import com.devflow.api.organization.domain.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    @Query("""
            select o from Organization o
            where exists (
                select m from OrganizationMember m
                where m.organization = o and m.user.id = :userId
            )
            order by o.createdAt desc
            """)
    List<Organization> findAllAccessibleTo(@Param("userId") UUID userId);
}
