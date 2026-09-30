package com.devflow.api.proposedchange.service;

import com.devflow.api.agent.domain.AgentExecutionContext;
import com.devflow.api.persistence.proposedchange.ProposedChangeRepository;
import com.devflow.api.persistence.task.TaskRepository;
import com.devflow.api.proposedchange.api.ProposedChangeCommand;
import com.devflow.api.proposedchange.api.ProposedChangeFileCommand;
import com.devflow.api.proposedchange.config.ProposedChangeProperties;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeFile;
import com.devflow.api.proposedchange.domain.ProposedChangeStatus;
import com.devflow.api.proposedchange.exception.ProposedChangeExceptions;
import com.devflow.api.proposedchange.validation.ProposedChangePatchValidator;
import com.devflow.api.proposedchange.validation.ProposedChangePathValidator;
import com.devflow.api.task.domain.Task;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposedChangeService {
    private final TaskRepository taskRepository;
    private final ProposedChangeRepository proposalRepository;
    private final ProposedChangeProperties properties;
    private final ProposedChangePatchValidator patchValidator;
    private final ProposedChangePathValidator pathValidator;

    public ProposedChangeService(TaskRepository taskRepository, ProposedChangeRepository proposalRepository,
            ProposedChangeProperties properties, ProposedChangePatchValidator patchValidator,
            ProposedChangePathValidator pathValidator) {
        this.taskRepository = taskRepository;
        this.proposalRepository = proposalRepository;
        this.properties = properties;
        this.patchValidator = patchValidator;
        this.pathValidator = pathValidator;
        properties.validate();
    }

    @Transactional
    public ProposedChange create(AgentExecutionContext context, ProposedChangeCommand command) {
        if (context == null || command == null || context.taskId() == null
                || context.organizationId() == null || context.repositoryId() == null) {
            throw ProposedChangeExceptions.validation();
        }
        validateText(command);
        Task task = taskRepository.findByIdAndOrganizationId(context.taskId(), context.organizationId())
                .orElseThrow(ProposedChangeExceptions::notFound);
        if (task.getRepository() == null || !context.repositoryId().equals(task.getRepository().getId())
                || !context.organizationId().equals(task.getRepository().getOrganization().getId())) {
            throw ProposedChangeExceptions.unauthorized();
        }
        if (!task.getRepository().isActive()) throw ProposedChangeExceptions.inactiveRepository();
        ProposedChangePatchValidator.ValidatedPatch validated = patchValidator.validate(command);
        ProposedChange proposal = new ProposedChange(task, task.getOrganization(), task.getRepository(),
                ProposedChangeStatus.VALIDATED, command.title().trim(), command.description().trim(),
                command.baseRevision().trim(), command.patch(), "{\"status\":\"VALIDATED\"}", Instant.now());
        for (ProposedChangeFileCommand file : command.files()) {
            proposal.addFile(new ProposedChangeFile(pathValidator.validate(file.path()), file.operation(),
                    file.additions(), file.deletions(), file.filePatch()));
        }
        return proposalRepository.save(proposal);
    }

    @Transactional(readOnly = true)
    public ProposedChange getById(UUID id, UUID organizationId) {
        if (id == null || organizationId == null) {
            throw ProposedChangeExceptions.notFound();
        }
        return proposalRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(ProposedChangeExceptions::notFound);
    }

    private void validateText(ProposedChangeCommand command) {
        if (command.title() == null || command.title().isBlank()
                || command.title().length() > properties.getMaxTitleCharacters()
                || command.description() == null || command.description().isBlank()
                || command.description().length() > properties.getMaxDescriptionCharacters()
                || command.baseRevision() == null || command.baseRevision().isBlank()
                || command.baseRevision().length() > 255) {
            throw ProposedChangeExceptions.validation();
        }
    }
}
