package com.devflow.api.approval.service;

import com.devflow.api.approval.api.ApproveApprovalRequest;
import com.devflow.api.approval.api.RejectApprovalRequest;
import com.devflow.api.approval.api.RequestApprovalRequest;
import com.devflow.api.approval.domain.Approval;
import com.devflow.api.approval.domain.ApprovalStatus;
import com.devflow.api.approval.exception.ApprovalExceptions;
import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.persistence.approval.ApprovalRepository;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.proposedchange.ProposedChangeRepository;
import com.devflow.api.persistence.sandbox.SandboxExecutionRepository;
import com.devflow.api.persistence.task.TaskEventRepository;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeStatus;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.task.domain.Task;
import com.devflow.api.task.domain.TaskActor;
import com.devflow.api.task.domain.TaskEvent;
import com.devflow.api.task.domain.TaskEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final ProposedChangeRepository proposedChangeRepository;
    private final SandboxExecutionRepository sandboxExecutionRepository;
    private final OrganizationMemberRepository memberRepository;
    private final TaskEventRepository taskEventRepository;
    private final ObjectMapper objectMapper;

    public ApprovalService(
            ApprovalRepository approvalRepository,
            ProposedChangeRepository proposedChangeRepository,
            SandboxExecutionRepository sandboxExecutionRepository,
            OrganizationMemberRepository memberRepository,
            TaskEventRepository taskEventRepository,
            ObjectMapper objectMapper) {
        this.approvalRepository = Objects.requireNonNull(approvalRepository, "approvalRepository must not be null");
        this.proposedChangeRepository = Objects.requireNonNull(proposedChangeRepository, "proposedChangeRepository must not be null");
        this.sandboxExecutionRepository = Objects.requireNonNull(sandboxExecutionRepository, "sandboxExecutionRepository must not be null");
        this.memberRepository = Objects.requireNonNull(memberRepository, "memberRepository must not be null");
        this.taskEventRepository = Objects.requireNonNull(taskEventRepository, "taskEventRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Transactional
    public Approval requestApproval(UUID principalId, UUID organizationId, RequestApprovalRequest request) {
        OrganizationMember member = requireMembership(principalId, organizationId);
        requireRequesterRole(member);

        ProposedChange proposedChange = proposedChangeRepository.findByIdAndOrganizationId(request.proposedChangeId(), organizationId)
                .orElseThrow(ApprovalExceptions::proposedChangeNotFound);

        if (proposedChange.getStatus() != ProposedChangeStatus.VALIDATED) {
            throw ApprovalExceptions.invalidProposalStatus();
        }

        if (!proposedChange.getRepository().isActive()) {
            throw ApprovalExceptions.inactiveRepository();
        }

        if (!proposedChange.getRepository().getOrganization().getId().equals(organizationId)) {
            throw ApprovalExceptions.organizationNotFound();
        }

        if (approvalRepository.existsByProposedChangeIdAndStatus(proposedChange.getId(), ApprovalStatus.PENDING)) {
            throw ApprovalExceptions.activeApprovalExists();
        }

        List<SandboxExecution> executions = sandboxExecutionRepository
                .findByProposedChangeIdOrderByCreatedAtDesc(proposedChange.getId());

        if (executions.isEmpty()) {
            throw ApprovalExceptions.noSuccessfulSandboxExecution();
        }

        SandboxExecution latestExecution = executions.get(0);
        if (latestExecution.getStatus() != SandboxExecutionStatus.SUCCEEDED) {
            throw ApprovalExceptions.noSuccessfulSandboxExecution();
        }

        if (latestExecution.getProposedChangeVersion() != proposedChange.getVersion()) {
            throw ApprovalExceptions.staleSandboxExecution();
        }

        if (!latestExecution.getOrganization().getId().equals(organizationId)
                || !latestExecution.getTask().getId().equals(proposedChange.getTask().getId())
                || !latestExecution.getRepository().getId().equals(proposedChange.getRepository().getId())) {
            throw ApprovalExceptions.organizationNotFound();
        }

        Instant now = Instant.now();
        Approval approval = new Approval(
                proposedChange,
                proposedChange.getTask(),
                proposedChange.getOrganization(),
                proposedChange.getRepository(),
                proposedChange.getVersion(),
                latestExecution,
                member.getUser(),
                now
        );

        approval = approvalRepository.saveAndFlush(approval);

        Task task = proposedChange.getTask();
        String metadataJson = createMetadataJson(approval, proposedChange, latestExecution, null);
        TaskEvent event = new TaskEvent(
                task,
                proposedChange.getOrganization(),
                TaskEventType.APPROVAL_REQUESTED,
                task.getStatus(),
                task.getStatus(),
                TaskActor.user(principalId),
                now,
                null,
                "approval-req-" + approval.getId(),
                metadataJson
        );
        taskEventRepository.save(event);

        return approval;
    }

    @Transactional
    public Approval approve(UUID principalId, UUID organizationId, UUID approvalId, ApproveApprovalRequest request) {
        OrganizationMember member = requireMembership(principalId, organizationId);
        requireDeciderRole(member);

        String comment = request != null ? request.comment() : null;
        validateText(comment, "comment", false);

        Approval approval = approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId)
                .orElseThrow(ApprovalExceptions::notFound);

        // Idempotency check: identical decision by the same decider
        if (approval.getStatus() == ApprovalStatus.APPROVED
                && approval.getDecidedBy() != null
                && principalId.equals(approval.getDecidedBy().getId())) {
            return approval;
        }

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw ApprovalExceptions.approvalAlreadyDecided();
        }

        ProposedChange proposedChange = approval.getProposedChange();
        if (proposedChange.getVersion() != approval.getProposedChangeVersion()) {
            throw ApprovalExceptions.proposedChangeStale();
        }

        if (proposedChange.getStatus() != ProposedChangeStatus.VALIDATED) {
            throw ApprovalExceptions.invalidProposalStatus();
        }

        if (!approval.getRepository().isActive()) {
            throw ApprovalExceptions.inactiveRepository();
        }

        SandboxExecution execution = approval.getSandboxExecution();
        if (execution.getStatus() != SandboxExecutionStatus.SUCCEEDED) {
            throw ApprovalExceptions.noSuccessfulSandboxExecution();
        }

        if (execution.getProposedChangeVersion() != approval.getProposedChangeVersion()) {
            throw ApprovalExceptions.staleSandboxExecution();
        }

        Instant now = Instant.now();
        if (!approval.approve(member.getUser(), comment, now)) {
            throw ApprovalExceptions.approvalAlreadyDecided();
        }

        try {
            approval = approvalRepository.saveAndFlush(approval);
        } catch (OptimisticLockingFailureException e) {
            throw ApprovalExceptions.transitionConflict();
        }

        Task task = approval.getTask();
        String metadataJson = createMetadataJson(approval, proposedChange, execution, comment);
        TaskEvent event = new TaskEvent(
                task,
                approval.getOrganization(),
                TaskEventType.APPROVAL_APPROVED,
                task.getStatus(),
                task.getStatus(),
                TaskActor.user(principalId),
                now,
                null,
                "approval-app-" + approval.getId(),
                metadataJson
        );
        taskEventRepository.save(event);

        return approval;
    }

    @Transactional
    public Approval reject(UUID principalId, UUID organizationId, UUID approvalId, RejectApprovalRequest request) {
        OrganizationMember member = requireMembership(principalId, organizationId);
        requireDeciderRole(member);

        String reason = request != null ? request.reason() : null;
        validateText(reason, "reason", true);

        Approval approval = approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId)
                .orElseThrow(ApprovalExceptions::notFound);

        // Idempotency check: identical decision by the same decider
        if (approval.getStatus() == ApprovalStatus.REJECTED
                && approval.getDecidedBy() != null
                && principalId.equals(approval.getDecidedBy().getId())) {
            return approval;
        }

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw ApprovalExceptions.approvalAlreadyDecided();
        }

        Instant now = Instant.now();
        if (!approval.reject(member.getUser(), reason, now)) {
            throw ApprovalExceptions.approvalAlreadyDecided();
        }

        try {
            approval = approvalRepository.saveAndFlush(approval);
        } catch (OptimisticLockingFailureException e) {
            throw ApprovalExceptions.transitionConflict();
        }

        Task task = approval.getTask();
        String metadataJson = createMetadataJson(approval, approval.getProposedChange(), approval.getSandboxExecution(), reason);
        TaskEvent event = new TaskEvent(
                task,
                approval.getOrganization(),
                TaskEventType.APPROVAL_REJECTED,
                task.getStatus(),
                task.getStatus(),
                TaskActor.user(principalId),
                now,
                null,
                "approval-rej-" + approval.getId(),
                metadataJson
        );
        taskEventRepository.save(event);

        return approval;
    }

    @Transactional(readOnly = true)
    public Approval getApproval(UUID principalId, UUID organizationId, UUID approvalId) {
        requireMembership(principalId, organizationId);
        return approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId)
                .orElseThrow(ApprovalExceptions::notFound);
    }

    @Transactional(readOnly = true)
    public Page<Approval> listApprovals(
            UUID principalId,
            UUID organizationId,
            UUID taskId,
            UUID proposedChangeId,
            Pageable pageable) {
        requireMembership(principalId, organizationId);

        if (taskId != null) {
            return approvalRepository.findAllByOrganizationIdAndTaskId(organizationId, taskId, pageable);
        }
        if (proposedChangeId != null) {
            return approvalRepository.findAllByOrganizationIdAndProposedChangeId(organizationId, proposedChangeId, pageable);
        }
        return approvalRepository.findAllByOrganizationId(organizationId, pageable);
    }

    private OrganizationMember requireMembership(UUID principalId, UUID organizationId) {
        if (principalId == null) {
            throw ApprovalExceptions.unauthorized();
        }
        return memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, principalId)
                .orElseThrow(ApprovalExceptions::organizationNotFound);
    }

    private void requireDeciderRole(OrganizationMember member) {
        if (member.getRole() != OrganizationRole.OWNER && member.getRole() != OrganizationRole.ADMIN) {
            throw ApprovalExceptions.forbidden("Only organization owners and admins can approve or reject changes");
        }
    }

    private void requireRequesterRole(OrganizationMember member) {
        if (member.getRole() == OrganizationRole.VIEWER) {
            throw ApprovalExceptions.forbidden("Viewers cannot request approvals");
        }
    }

    private void validateText(String text, String fieldName, boolean required) {
        if (required && (text == null || text.isBlank())) {
            throw ApprovalExceptions.invalidReason(fieldName + " must not be blank");
        }
        if (text != null) {
            if (text.length() > 1000) {
                throw ApprovalExceptions.invalidReason(fieldName + " must not exceed 1000 characters");
            }
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 && c != '\t' && c != '\r' && c != '\n') {
                    throw ApprovalExceptions.invalidReason(fieldName + " contains illegal control characters");
                }
            }
        }
    }

    private String createMetadataJson(Approval approval, ProposedChange proposal, SandboxExecution execution, String note) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("approvalId", approval.getId().toString());
        node.put("proposedChangeId", proposal.getId().toString());
        node.put("proposedChangeVersion", approval.getProposedChangeVersion());
        if (execution != null) {
            node.put("sandboxExecutionId", execution.getId().toString());
            node.put("sandboxExecutionStatus", execution.getStatus().name());
        }
        if (note != null && !note.isBlank()) {
            node.put("note", note.trim());
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"approvalId\":\"" + approval.getId() + "\"}";
        }
    }
}
