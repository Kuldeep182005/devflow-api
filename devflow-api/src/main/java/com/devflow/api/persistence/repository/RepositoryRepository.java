package com.devflow.api.persistence.repository;

import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositoryRepository extends JpaRepository<Repository, UUID> {

    Optional<Repository> findByProviderAndExternalId(RepositoryProvider provider, String externalId);

    List<Repository> findAllByProviderAndExternalId(RepositoryProvider provider, String externalId);

    Optional<Repository> findByOrganizationIdAndProviderAndExternalId(
            UUID organizationId, RepositoryProvider provider, String externalId);

    boolean existsByIdAndOrganizationId(UUID repositoryId, UUID organizationId);

    @Query(value = """
            select r from Repository r
            join fetch r.organization
            where r.organization.id = :organizationId
            """,
            countQuery = "select count(r) from Repository r where r.organization.id = :organizationId")
    Page<Repository> findAllByOrganizationId(@Param("organizationId") UUID organizationId, Pageable pageable);

    @Query("""
            select r from Repository r
            join fetch r.organization
            where r.id = :repositoryId and r.organization.id = :organizationId
            """)
    Optional<Repository> findByIdAndOrganizationId(
            @Param("repositoryId") UUID repositoryId, @Param("organizationId") UUID organizationId);
}
