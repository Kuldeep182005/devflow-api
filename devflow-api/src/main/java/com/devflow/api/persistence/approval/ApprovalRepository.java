package com.devflow.api.persistence.approval;

import com.devflow.api.approval.domain.Approval;
import com.devflow.api.approval.domain.ApprovalStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalRepository extends JpaRepository<Approval, UUID> {

    @Query("""
            select a from Approval a
            join fetch a.proposedChange
            join fetch a.task
            join fetch a.organization
            join fetch a.repository
            join fetch a.sandboxExecution
            join fetch a.requestedBy
            left join fetch a.decidedBy
            where a.id = :id and a.organization.id = :organizationId
            """)
    Optional<Approval> findByIdAndOrganizationIdWithDetails(
            @Param("id") UUID id, @Param("organizationId") UUID organizationId);

    @Query("""
            select a from Approval a
            join fetch a.proposedChange
            join fetch a.task
            join fetch a.organization
            join fetch a.repository
            join fetch a.sandboxExecution
            join fetch a.requestedBy
            left join fetch a.decidedBy
            where a.id = :id
            """)
    Optional<Approval> findByIdWithDetails(@Param("id") UUID id);

    Optional<Approval> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByProposedChangeIdAndStatus(UUID proposedChangeId, ApprovalStatus status);

    boolean existsByProposedChangeIdAndProposedChangeVersionAndStatus(
            UUID proposedChangeId, long proposedChangeVersion, ApprovalStatus status);

    Page<Approval> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Approval> findAllByOrganizationIdAndTaskId(UUID organizationId, UUID taskId, Pageable pageable);

    Page<Approval> findAllByOrganizationIdAndProposedChangeId(UUID organizationId, UUID proposedChangeId, Pageable pageable);
}
