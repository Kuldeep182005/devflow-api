package com.devflow.api.github.mutation.service;

import com.devflow.api.approval.domain.Approval;
import com.devflow.api.approval.domain.ApprovalStatus;
import com.devflow.api.async.persistence.AsyncJobStore;
import com.devflow.api.github.mutation.api.GithubMutationResponse;
import com.devflow.api.github.mutation.domain.GithubMutation;
import com.devflow.api.github.mutation.domain.GithubMutationStatus;
import com.devflow.api.github.mutation.exception.GithubMutationExceptions;
import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.persistence.approval.ApprovalRepository;
import com.devflow.api.persistence.githubmutation.GithubMutationRepository;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.proposedchange.domain.ProposedChange;
import com.devflow.api.proposedchange.domain.ProposedChangeStatus;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.sandbox.domain.SandboxExecution;
import com.devflow.api.sandbox.domain.SandboxExecutionStatus;
import com.devflow.api.security.AuthenticatedUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GithubMutationService {

    private final GithubMutationRepository githubMutationRepository;
    private final ApprovalRepository approvalRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AsyncJobStore asyncJobStore;
    private final com.devflow.api.observability.metrics.DevFlowMetrics metrics;

    public GithubMutationService(
            GithubMutationRepository githubMutationRepository,
            ApprovalRepository approvalRepository,
            OrganizationMemberRepository memberRepository,
            AsyncJobStore asyncJobStore) {
        this(githubMutationRepository, approvalRepository, memberRepository, asyncJobStore, (com.devflow.api.observability.metrics.DevFlowMetrics) null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GithubMutationService(
            GithubMutationRepository githubMutationRepository,
            ApprovalRepository approvalRepository,
            OrganizationMemberRepository memberRepository,
            AsyncJobStore asyncJobStore,
            org.springframework.beans.factory.ObjectProvider<com.devflow.api.observability.metrics.DevFlowMetrics> metricsProvider) {
        this(githubMutationRepository, approvalRepository, memberRepository, asyncJobStore,
                metricsProvider != null ? metricsProvider.getIfAvailable() : null);
    }

    public GithubMutationService(
            GithubMutationRepository githubMutationRepository,
            ApprovalRepository approvalRepository,
            OrganizationMemberRepository memberRepository,
            AsyncJobStore asyncJobStore,
            com.devflow.api.observability.metrics.DevFlowMetrics metrics) {
        this.githubMutationRepository = githubMutationRepository;
        this.approvalRepository = approvalRepository;
        this.memberRepository = memberRepository;
        this.asyncJobStore = asyncJobStore;
        this.metrics = metrics;
    }


    @Transactional
    public GithubMutationResponse enqueueExecution(UUID userId, UUID organizationId, UUID approvalId) {
        if (userId == null) {
            throw GithubMutationExceptions.unauthenticated();
        }
        if (organizationId == null || approvalId == null) {
            throw GithubMutationExceptions.approvalNotFound();
        }

        // 1. Verify DB-backed organization membership and role permissions
        OrganizationMember member = memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId)
                .orElseThrow(GithubMutationExceptions::organizationNotFound);

        if (member.getRole() != OrganizationRole.OWNER && member.getRole() != OrganizationRole.ADMIN) {
            throw GithubMutationExceptions.forbidden();
        }

        // 2. Load Approval with tenant scope and fetch joined details
        Approval approval = approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId)
                .orElseThrow(GithubMutationExceptions::approvalNotFound);

        // 3. Approval must be in APPROVED status
        if (approval.getStatus() != ApprovalStatus.APPROVED) {
            throw GithubMutationExceptions.approvalNotApproved();
        }

        // 4. ProposedChange must match exact approved version and be VALIDATED
        ProposedChange proposedChange = approval.getProposedChange();
        if (proposedChange.getVersion() != approval.getProposedChangeVersion()) {
            throw GithubMutationExceptions.proposalVersionMismatch();
        }
        if (proposedChange.getStatus() != ProposedChangeStatus.VALIDATED) {
            throw GithubMutationExceptions.proposalVersionMismatch();
        }

        // 5. SandboxExecution must be SUCCEEDED and reference the exact proposal version
        SandboxExecution execution = approval.getSandboxExecution();
        if (execution.getStatus() != SandboxExecutionStatus.SUCCEEDED) {
            throw GithubMutationExceptions.sandboxNotSuccessful();
        }
        if (execution.getProposedChangeVersion() != approval.getProposedChangeVersion()) {
            throw GithubMutationExceptions.sandboxNotSuccessful();
        }

        // 6. Repository must be active and linked to GitHub installation
        Repository repository = approval.getRepository();
        if (!repository.isActive() || repository.getGithubInstallationId() == null || repository.getGithubInstallationId() <= 0) {
            throw GithubMutationExceptions.installationNotFound();
        }

        if (metrics != null) {
            metrics.recordGithubMutationAttempt();
        }

        // 7. Check for existing mutation record (idempotency)
        Optional<GithubMutation> existing = githubMutationRepository
                .findByOrganizationIdAndRepositoryIdAndProposedChangeIdAndProposedChangeVersion(
                        organizationId, repository.getId(), proposedChange.getId(), proposedChange.getVersion());

        Instant now = Instant.now();
        if (existing.isPresent()) {
            if (metrics != null) {
                metrics.recordGithubReconciliation("existing_record");
            }
            GithubMutation mutation = existing.get();

            if (mutation.getStatus() == GithubMutationStatus.FAILED) {
                mutation.markRunning(now);
                mutation = githubMutationRepository.saveAndFlush(mutation);
                asyncJobStore.enqueueGithubPrCreation(mutation.getId());
            }
            return GithubMutationResponse.from(mutation);
        }

        // 8. Generate deterministic branch name and persist new mutation record
        String branchName = "devflow/task-" + approval.getTask().getId() + "/change-" + proposedChange.getId();
        GithubMutation mutation = new GithubMutation(
                UUID.randomUUID(),
                approval.getOrganization(),
                repository,
                approval.getTask(),
                proposedChange,
                proposedChange.getVersion(),
                approval,
                execution,
                proposedChange.getBaseRevision(),
                branchName,
                now
        );

        mutation = githubMutationRepository.saveAndFlush(mutation);

        // 9. Enqueue background Redis job
        asyncJobStore.enqueueGithubPrCreation(mutation.getId());

        return GithubMutationResponse.from(mutation);
    }

    @Transactional(readOnly = true)
    public GithubMutationResponse getMutationByApprovalId(UUID userId, UUID organizationId, UUID approvalId) {
        if (userId == null) {
            throw GithubMutationExceptions.unauthenticated();
        }
        if (organizationId == null || approvalId == null) {
            throw GithubMutationExceptions.approvalNotFound();
        }

        memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId)
                .orElseThrow(GithubMutationExceptions::organizationNotFound);

        approvalRepository.findByIdAndOrganizationIdWithDetails(approvalId, organizationId)
                .orElseThrow(GithubMutationExceptions::approvalNotFound);

        GithubMutation mutation = githubMutationRepository.findByApprovalIdAndOrganizationId(approvalId, organizationId)
                .orElseThrow(GithubMutationExceptions::notFound);

        return GithubMutationResponse.from(mutation);
    }

    @Transactional(readOnly = true)
    public GithubMutationResponse getMutationById(UUID userId, UUID organizationId, UUID mutationId) {
        if (userId == null) {
            throw GithubMutationExceptions.unauthenticated();
        }
        if (organizationId == null || mutationId == null) {
            throw GithubMutationExceptions.notFound();
        }

        memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId)
                .orElseThrow(GithubMutationExceptions::organizationNotFound);

        GithubMutation mutation = githubMutationRepository.findByIdAndOrganizationIdWithDetails(mutationId, organizationId)
                .orElseThrow(GithubMutationExceptions::notFound);

        return GithubMutationResponse.from(mutation);
    }
}
