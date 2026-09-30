package com.devflow.api.github.mutation.api;

import com.devflow.api.github.mutation.domain.GithubMutation;
import java.time.Instant;
import java.util.UUID;

public record GithubMutationResponse(
        UUID id,
        UUID organizationId,
        UUID repositoryId,
        UUID taskId,
        UUID proposedChangeId,
        long proposedChangeVersion,
        UUID approvalId,
        UUID sandboxExecutionId,
        String baseRevision,
        String branchName,
        String commitSha,
        Long pullRequestNumber,
        String pullRequestUrl,
        String status,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static GithubMutationResponse from(GithubMutation mutation) {
        if (mutation == null) {
            return null;
        }
        return new GithubMutationResponse(
                mutation.getId(),
                mutation.getOrganization().getId(),
                mutation.getRepository().getId(),
                mutation.getTask().getId(),
                mutation.getProposedChange().getId(),
                mutation.getProposedChangeVersion(),
                mutation.getApproval().getId(),
                mutation.getSandboxExecution().getId(),
                mutation.getBaseRevision(),
                mutation.getBranchName(),
                mutation.getCommitSha(),
                mutation.getPullRequestNumber(),
                mutation.getPullRequestUrl(),
                mutation.getStatus().name(),
                mutation.getFailureCode(),
                mutation.getFailureMessage(),
                mutation.getCreatedAt(),
                mutation.getUpdatedAt(),
                mutation.getVersion()
        );
    }
}
