package com.devflow.api.approval.api;

import com.devflow.api.approval.service.ApprovalService;
import com.devflow.api.github.mutation.api.GithubMutationResponse;
import com.devflow.api.github.mutation.service.GithubMutationService;
import com.devflow.api.organization.api.PageResponse;
import com.devflow.api.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final GithubMutationService githubMutationService;

    public ApprovalController(
            ApprovalService approvalService,
            GithubMutationService githubMutationService) {
        this.approvalService = approvalService;
        this.githubMutationService = githubMutationService;
    }

    @PostMapping
    public ResponseEntity<ApprovalResponse> requestApproval(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @Valid @RequestBody RequestApprovalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApprovalResponse.from(approvalService.requestApproval(principal.id(), organizationId, request)));
    }

    @GetMapping("/{approvalId}")
    public ApprovalResponse get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PathVariable UUID approvalId) {
        return ApprovalResponse.from(approvalService.getApproval(principal.id(), organizationId, approvalId));
    }

    @GetMapping
    public PageResponse<ApprovalResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @RequestParam(required = false) UUID taskId,
            @RequestParam(required = false) UUID proposedChangeId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return page(approvalService.listApprovals(principal.id(), organizationId, taskId, proposedChangeId, pageable)
                .map(ApprovalResponse::from));
    }

    @PostMapping("/{approvalId}/approve")
    public ApprovalResponse approve(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PathVariable UUID approvalId,
            @Valid @RequestBody(required = false) ApproveApprovalRequest request) {
        return ApprovalResponse.from(approvalService.approve(principal.id(), organizationId, approvalId, request));
    }

    @PostMapping("/{approvalId}/reject")
    public ApprovalResponse reject(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PathVariable UUID approvalId,
            @Valid @RequestBody RejectApprovalRequest request) {
        return ApprovalResponse.from(approvalService.reject(principal.id(), organizationId, approvalId, request));
    }

    @PostMapping("/{approvalId}/execute")
    public ResponseEntity<GithubMutationResponse> execute(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PathVariable UUID approvalId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(githubMutationService.enqueueExecution(principal.id(), organizationId, approvalId));
    }

    @GetMapping("/{approvalId}/mutation")
    public GithubMutationResponse getMutation(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PathVariable UUID approvalId) {
        return githubMutationService.getMutationByApprovalId(principal.id(), organizationId, approvalId);
    }

    private static <T> PageResponse<T> page(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
