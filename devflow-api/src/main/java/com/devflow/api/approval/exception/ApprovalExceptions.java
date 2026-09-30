package com.devflow.api.approval.exception;

import org.springframework.http.HttpStatus;

public final class ApprovalExceptions {

    private ApprovalExceptions() {
    }

    public static ApprovalException notFound() {
        return new ApprovalException(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND",
                "The approval was not found");
    }

    public static ApprovalException proposedChangeNotFound() {
        return new ApprovalException(HttpStatus.NOT_FOUND, "PROPOSED_CHANGE_NOT_FOUND",
                "The proposed change was not found");
    }

    public static ApprovalException organizationNotFound() {
        return new ApprovalException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND",
                "The organization does not exist");
    }

    public static ApprovalException unauthorized() {
        return new ApprovalException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication is required");
    }

    public static ApprovalException forbidden(String message) {
        return new ApprovalException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApprovalException invalidProposalStatus() {
        return new ApprovalException(HttpStatus.CONFLICT, "INVALID_PROPOSED_CHANGE_STATUS",
                "Only validated proposed changes may be submitted for or receive approval");
    }

    public static ApprovalException inactiveRepository() {
        return new ApprovalException(HttpStatus.CONFLICT, "REPOSITORY_INACTIVE",
                "The repository is inactive");
    }

    public static ApprovalException noSuccessfulSandboxExecution() {
        return new ApprovalException(HttpStatus.CONFLICT, "NO_SUCCESSFUL_SANDBOX_EXECUTION",
                "A successful sandbox execution is required before requesting or granting approval");
    }

    public static ApprovalException staleSandboxExecution() {
        return new ApprovalException(HttpStatus.CONFLICT, "STALE_SANDBOX_EXECUTION",
                "The sandbox execution does not match the exact proposed change version");
    }

    public static ApprovalException activeApprovalExists() {
        return new ApprovalException(HttpStatus.CONFLICT, "ACTIVE_APPROVAL_EXISTS",
                "An active pending approval already exists for this proposed change");
    }

    public static ApprovalException approvalAlreadyDecided() {
        return new ApprovalException(HttpStatus.CONFLICT, "APPROVAL_ALREADY_DECIDED",
                "The approval has already been decided");
    }

    public static ApprovalException proposedChangeStale() {
        return new ApprovalException(HttpStatus.CONFLICT, "PROPOSED_CHANGE_STALE",
                "The proposed change version does not match the reviewed version");
    }

    public static ApprovalException transitionConflict() {
        return new ApprovalException(HttpStatus.CONFLICT, "APPROVAL_TRANSITION_CONFLICT",
                "The approval was modified by a concurrent request");
    }

    public static ApprovalException invalidReason(String message) {
        return new ApprovalException(HttpStatus.BAD_REQUEST, "INVALID_APPROVAL_REASON", message);
    }
}
