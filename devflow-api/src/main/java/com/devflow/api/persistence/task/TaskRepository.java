package com.devflow.api.persistence.task;

import com.devflow.api.task.domain.Task;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Query(value = """
            select t from Task t
            join fetch t.organization
            join fetch t.repository
            join fetch t.createdBy
            where t.organization.id = :organizationId
            """,
            countQuery = "select count(t) from Task t where t.organization.id = :organizationId")
    Page<Task> findAllByOrganizationId(@Param("organizationId") UUID organizationId, Pageable pageable);

    @Query(value = """
            select t from Task t
            join fetch t.organization
            join fetch t.repository
            join fetch t.createdBy
            where t.repository.id = :repositoryId
            """,
            countQuery = "select count(t) from Task t where t.repository.id = :repositoryId")
    Page<Task> findAllByRepositoryId(@Param("repositoryId") UUID repositoryId, Pageable pageable);

    @Query(value = """
            select t from Task t
            join fetch t.organization
            join fetch t.repository
            join fetch t.createdBy
            where t.organization.id = :organizationId and t.repository.id = :repositoryId
            """,
            countQuery = """
                    select count(t) from Task t
                    where t.organization.id = :organizationId and t.repository.id = :repositoryId
                    """)
    Page<Task> findAllByOrganizationIdAndRepositoryId(
            @Param("organizationId") UUID organizationId,
            @Param("repositoryId") UUID repositoryId,
            Pageable pageable);

    @Query("""
            select t from Task t
            join fetch t.organization
            join fetch t.repository
            join fetch t.createdBy
            where t.id = :taskId and t.organization.id = :organizationId
            """)
    java.util.Optional<Task> findByIdAndOrganizationId(
            @Param("taskId") UUID taskId, @Param("organizationId") UUID organizationId);
}
