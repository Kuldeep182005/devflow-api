package com.devflow.api.approval.api;

import com.devflow.api.approval.domain.Approval;
import com.devflow.api.approval.domain.ApprovalStatus;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import java.time.Instant;
import java.util.UUID;

public record ApprovalResponse(
        UUID id,
        UUID proposedChangeId,
        UUID taskId,
        UUID repositoryId,
        UUID organizationId,
        ApprovalStatus status,
        long proposedChangeVersion,
        UUID sandboxExecutionId,
        SandboxExecutionStatus sandboxExecutionStatus,
        UUID requestedByUserId,
        UUID decidedByUserId,
        String decisionReason,
        Instant requestedAt,
        Instant decidedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static ApprovalResponse from(Approval approval) {
        return new ApprovalResponse(
                approval.getId(),
                approval.getProposedChange().getId(),
                approval.getTask().getId(),
                approval.getRepository().getId(),
                approval.getOrganization().getId(),
                approval.getStatus(),
                approval.getProposedChangeVersion(),
                approval.getSandboxExecution().getId(),
                approval.getSandboxExecution().getStatus(),
                approval.getRequestedBy().getId(),
                approval.getDecidedBy() != null ? approval.getDecidedBy().getId() : null,
                approval.getDecisionReason(),
                approval.getRequestedAt(),
                approval.getDecidedAt(),
                approval.getCreatedAt(),
                approval.getUpdatedAt(),
                approval.getVersion()
        );
    }
}
