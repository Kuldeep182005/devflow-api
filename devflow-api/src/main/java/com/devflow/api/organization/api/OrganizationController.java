package com.devflow.api.organization.api;

import com.devflow.api.organization.service.OrganizationService;
import com.devflow.api.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<OrganizationResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrganizationResponse.from(organizationService.create(principal.id(), request)));
    }

    @GetMapping
    public List<OrganizationResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return organizationService.list(principal.id()).stream().map(OrganizationResponse::from).toList();
    }

    @GetMapping("/{organizationId}")
    public OrganizationResponse get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId) {
        return OrganizationResponse.from(organizationService.get(principal.id(), organizationId));
    }

    @GetMapping("/{organizationId}/members")
    public PageResponse<OrganizationMemberResponse> members(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return page(organizationService.listMembers(principal.id(), organizationId, pageable)
                .map(OrganizationMemberResponse::from));
    }

    @PostMapping("/{organizationId}/members")
    public ResponseEntity<OrganizationMemberResponse> addMember(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @Valid @RequestBody AddOrganizationMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrganizationMemberResponse.from(
                        organizationService.addMember(principal.id(), organizationId, request)));
    }

    @PatchMapping("/{organizationId}/members/{userId}")
    public OrganizationMemberResponse updateMember(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateOrganizationMemberRequest request) {
        return OrganizationMemberResponse.from(
                organizationService.updateMember(principal.id(), organizationId, userId, request));
    }

    @PutMapping("/{organizationId}/members/{userId}")
    public OrganizationMemberResponse replaceMember(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateOrganizationMemberRequest request) {
        return updateMember(principal, organizationId, userId, request);
    }

    @DeleteMapping("/{organizationId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PathVariable UUID userId) {
        organizationService.removeMember(principal.id(), organizationId, userId);
        return ResponseEntity.noContent().build();
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
