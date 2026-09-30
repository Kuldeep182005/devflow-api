package com.devflow.api.persistence.sandbox;

import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SandboxExecutionRepository extends JpaRepository<SandboxExecution, UUID> {

    @Query("""
            select s from SandboxExecution s
            join fetch s.proposedChange pc
            left join fetch pc.files
            join fetch s.task
            join fetch s.organization
            join fetch s.repository
            where s.id = :id
            """)
    Optional<SandboxExecution> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            select s from SandboxExecution s
            join fetch s.proposedChange pc
            left join fetch pc.files
            join fetch s.task
            join fetch s.organization
            join fetch s.repository
            where s.id = :id and s.organization.id = :organizationId
            """)
    Optional<SandboxExecution> findByIdAndOrganizationId(
            @Param("id") UUID id, @Param("organizationId") UUID organizationId);

    List<SandboxExecution> findByProposedChangeIdOrderByCreatedAtDesc(UUID proposedChangeId);

    List<SandboxExecution> findByTaskIdOrderByCreatedAtDesc(UUID taskId);

    List<SandboxExecution> findByStatus(SandboxExecutionStatus status);
}
