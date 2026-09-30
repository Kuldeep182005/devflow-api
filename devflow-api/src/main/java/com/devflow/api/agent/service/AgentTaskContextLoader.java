package com.devflow.api.agent.service;

import com.devflow.api.agent.domain.AgentExecutionContext;
import com.devflow.api.agent.exception.AgentExecutionException;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.task.TaskRepository;
import com.devflow.api.task.domain.Task;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentTaskContextLoader {
    private final TaskRepository taskRepository;
    private final OrganizationMemberRepository memberRepository;

    public AgentTaskContextLoader(TaskRepository taskRepository, OrganizationMemberRepository memberRepository) {
        this.taskRepository = taskRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public AgentExecutionContext load(UUID userId, UUID organizationId, UUID taskId) {
        if (!memberRepository.existsByIdUserIdAndIdOrganizationId(userId, organizationId)) {
            throw new AgentExecutionException("Agent task authorization failed");
        }
        Task task = taskRepository.findByIdAndOrganizationId(taskId, organizationId)
                .orElseThrow(() -> new AgentExecutionException("Agent task was not found"));
        if (task.getRepository() == null || task.getOrganization() == null
                || !organizationId.equals(task.getOrganization().getId())
                || !organizationId.equals(task.getRepository().getOrganization().getId())
                || !task.getRepository().isActive()) {
            throw new AgentExecutionException("Agent task authorization failed");
        }
        return new AgentExecutionContext(userId, organizationId, taskId, task.getRepository().getId(),
                task.getTitle() + "\n" + (task.getDescription() == null ? "" : task.getDescription()),
                task.getRepository().getFullName());
    }
}
