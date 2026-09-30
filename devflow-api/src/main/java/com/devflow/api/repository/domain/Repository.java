package com.devflow.api.repository.domain;

import com.devflow.api.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "repositories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "repositories_organization_provider_external_id_unique",
                        columnNames = {"organization_id", "provider", "external_id"})
        }
)
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private RepositoryProvider provider;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Column(name = "github_installation_id")
    private Long githubInstallationId;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "default_branch", nullable = false, length = 255)
    private String defaultBranch;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Repository() {
    }

    public Repository(
            Organization organization,
            RepositoryProvider provider,
            String externalId,
            String fullName,
            String defaultBranch,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this.organization = organization;
        this.provider = provider;
        this.externalId = externalId;
        this.githubInstallationId = null;
        this.fullName = fullName;
        this.defaultBranch = defaultBranch;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public RepositoryProvider getProvider() {
        return provider;
    }

    public String getExternalId() {
        return externalId;
    }

    public Long getGithubInstallationId() {
        return githubInstallationId;
    }

    public void associateGithubInstallation(long installationId) {
        if (installationId <= 0) {
            throw new IllegalArgumentException("GitHub installation id must be positive");
        }
        this.githubInstallationId = installationId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
