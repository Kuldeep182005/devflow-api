package com.devflow.api.observability.evaluation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AiEvaluationRepository extends JpaRepository<AiEvaluation, UUID> {

    Optional<AiEvaluation> findByExecutionId(UUID executionId);

    List<AiEvaluation> findByTaskIdOrderByStartedAtDesc(UUID taskId);

    @Query("SELECT e FROM AiEvaluation e WHERE e.organization.id = :orgId ORDER BY e.startedAt DESC")
    List<AiEvaluation> findByOrganizationId(@Param("orgId") UUID organizationId);
}
