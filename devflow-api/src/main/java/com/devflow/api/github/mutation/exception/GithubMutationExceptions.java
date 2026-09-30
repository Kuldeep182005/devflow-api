package com.devflow.api.github.mutation.exception;

import org.springframework.http.HttpStatus;

public final class GithubMutationExceptions {

    private GithubMutationExceptions() {
    }

    public static GithubMutationException notFound() {
        return new GithubMutationException(HttpStatus.NOT_FOUND, "GITHUB_MUTATION_NOT_FOUND",
                "GitHub mutation record was not found");
    }

    public static GithubMutationException organizationNotFound() {
        return new GithubMutationException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND",
                "The organization was not found");
    }

    public static GithubMutationException approvalNotFound() {
        return new GithubMutationException(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND",
                "The approval was not found");
    }

    public static GithubMutationException forbidden() {
        return new GithubMutationException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "User lacks permission to execute approved mutation");
    }

    public static GithubMutationException unauthenticated() {
        return new GithubMutationException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication is required");
    }

    public static GithubMutationException approvalNotApproved() {
        return new GithubMutationException(HttpStatus.CONFLICT, "APPROVAL_NOT_APPROVED",
                "Approval must be in APPROVED status before execution");
    }

    public static GithubMutationException approvalStale() {
        return new GithubMutationException(HttpStatus.CONFLICT, "APPROVAL_STALE",
                "The approval is stale and no longer valid");
    }

    public static GithubMutationException proposalVersionMismatch() {
        return new GithubMutationException(HttpStatus.CONFLICT, "PROPOSAL_VERSION_MISMATCH",
                "Proposed change version does not match the approved version");
    }

    public static GithubMutationException sandboxNotSuccessful() {
        return new GithubMutationException(HttpStatus.CONFLICT, "SANDBOX_NOT_SUCCESSFUL",
                "A successful sandbox execution matching the proposal version is required");
    }

    public static GithubMutationException baseRevisionStale() {
        return new GithubMutationException(HttpStatus.CONFLICT, "BASE_REVISION_STALE",
                "Remote base branch revision does not match approved base revision");
    }

    public static GithubMutationException branchConflict(String message) {
        return new GithubMutationException(HttpStatus.CONFLICT, "BRANCH_CONFLICT",
                message != null ? message : "Conflict detected on target branch");
    }

    public static GithubMutationException patchApplicationFailed(String message) {
        return new GithubMutationException(HttpStatus.CONFLICT, "PATCH_APPLICATION_FAILED",
                message != null ? message : "Failed to apply approved patch to remote base content");
    }

    public static GithubMutationException installationNotFound() {
        return new GithubMutationException(HttpStatus.CONFLICT, "GITHUB_INSTALLATION_NOT_FOUND",
                "GitHub App installation not found for repository");
    }
}
