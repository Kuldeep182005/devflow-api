package com.devflow.api.persistence.githubmutation;

import com.devflow.api.github.mutation.domain.GithubMutation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GithubMutationRepository extends JpaRepository<GithubMutation, UUID> {

    Optional<GithubMutation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<GithubMutation> findByApprovalIdAndOrganizationId(UUID approvalId, UUID organizationId);

    Optional<GithubMutation> findByOrganizationIdAndRepositoryIdAndProposedChangeIdAndProposedChangeVersion(
            UUID organizationId,
            UUID repositoryId,
            UUID proposedChangeId,
            long proposedChangeVersion);

    @Query("""
            SELECT m FROM GithubMutation m
            JOIN FETCH m.organization
            JOIN FETCH m.repository
            JOIN FETCH m.task
            JOIN FETCH m.proposedChange pc
            LEFT JOIN FETCH pc.files
            JOIN FETCH m.approval
            JOIN FETCH m.sandboxExecution
            WHERE m.id = :id
            """)
    Optional<GithubMutation> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            SELECT m FROM GithubMutation m
            JOIN FETCH m.organization
            JOIN FETCH m.repository
            JOIN FETCH m.task
            JOIN FETCH m.proposedChange pc
            LEFT JOIN FETCH pc.files
            JOIN FETCH m.approval
            JOIN FETCH m.sandboxExecution
            WHERE m.id = :id AND m.organization.id = :organizationId
            """)
    Optional<GithubMutation> findByIdAndOrganizationIdWithDetails(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId);
}
