package com.devflow.api.proposedchange.domain;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.task.domain.Task;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "proposed_changes")
public class ProposedChange {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposedChangeStatus status;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(name = "base_revision", nullable = false, length = 255)
    private String baseRevision;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String patch;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_metadata", nullable = false, columnDefinition = "jsonb")
    private String validationMetadata;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "proposedChange", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ProposedChangeFile> files = new ArrayList<>();

    protected ProposedChange() {
    }

    public ProposedChange(Task task, Organization organization, Repository repository,
            ProposedChangeStatus status, String title, String description, String baseRevision,
            String patch, String validationMetadata, Instant now) {
        this.task = task;
        this.organization = organization;
        this.repository = repository;
        this.status = status;
        this.title = title;
        this.description = description;
        this.baseRevision = baseRevision;
        this.patch = patch;
        this.validationMetadata = validationMetadata;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addFile(ProposedChangeFile file) {
        file.attach(this);
        files.add(file);
    }

    public UUID getId() { return id; }
    public Task getTask() { return task; }
    public Organization getOrganization() { return organization; }
    public Repository getRepository() { return repository; }
    public ProposedChangeStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getBaseRevision() { return baseRevision; }
    public String getPatch() { return patch; }
    public String getValidationMetadata() { return validationMetadata; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ProposedChangeFile> getFiles() { return List.copyOf(files); }
}
