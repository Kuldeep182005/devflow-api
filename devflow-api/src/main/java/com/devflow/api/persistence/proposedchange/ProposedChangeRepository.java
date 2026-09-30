package com.devflow.api.persistence.proposedchange;

import com.devflow.api.proposedchange.domain.ProposedChange;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProposedChangeRepository extends JpaRepository<ProposedChange, UUID> {
    @Query("""
            select p from ProposedChange p
            join fetch p.task
            join fetch p.organization
            join fetch p.repository
            where p.id = :id and p.organization.id = :organizationId
            """)
    Optional<ProposedChange> findByIdAndOrganizationId(
            @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
