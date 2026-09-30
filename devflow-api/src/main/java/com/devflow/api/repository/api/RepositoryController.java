package com.devflow.api.repository.api;

import com.devflow.api.organization.api.PageResponse;
import com.devflow.api.repository.service.RepositoryService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @PostMapping
    public ResponseEntity<RepositoryResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateRepositoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RepositoryResponse.from(repositoryService.create(principal.id(), organizationId, request)));
    }

    @PostMapping("/connect")
    public ResponseEntity<RepositoryResponse> connect(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @Valid @RequestBody ConnectRepositoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RepositoryResponse.from(repositoryService.connect(
                        principal.id(), organizationId, request)));
    }

    @GetMapping
    public PageResponse<RepositoryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return page(repositoryService.list(principal.id(), organizationId, pageable).map(RepositoryResponse::from));
    }

    @GetMapping("/{repositoryId}")
    public RepositoryResponse get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId) {
        return RepositoryResponse.from(repositoryService.get(principal.id(), organizationId, repositoryId));
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
