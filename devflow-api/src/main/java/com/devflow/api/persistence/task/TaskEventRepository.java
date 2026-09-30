package com.devflow.api.persistence.task;

import com.devflow.api.task.domain.TaskEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskEventRepository extends JpaRepository<TaskEvent, UUID> {

    Page<TaskEvent> findAllByTaskIdAndOrganizationId(UUID taskId, UUID organizationId, Pageable pageable);

    java.util.Optional<TaskEvent> findByTaskIdAndIdempotencyKey(UUID taskId, String idempotencyKey);
}
