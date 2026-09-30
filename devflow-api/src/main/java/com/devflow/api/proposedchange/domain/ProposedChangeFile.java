package com.devflow.api.proposedchange.domain;

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
import java.util.UUID;

@Entity
@Table(name = "proposed_change_files",
        uniqueConstraints = @UniqueConstraint(name = "proposed_change_files_unique_path",
                columnNames = {"proposed_change_id", "path"}))
public class ProposedChangeFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposed_change_id", nullable = false)
    private ProposedChange proposedChange;

    @Column(nullable = false, length = 1000)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProposedChangeOperation operation;

    @Column(nullable = false)
    private int additions;

    @Column(nullable = false)
    private int deletions;

    @Column(name = "file_patch", nullable = false, columnDefinition = "TEXT")
    private String filePatch;

    protected ProposedChangeFile() {
    }

    public ProposedChangeFile(String path, ProposedChangeOperation operation, int additions,
            int deletions, String filePatch) {
        this.path = path;
        this.operation = operation;
        this.additions = additions;
        this.deletions = deletions;
        this.filePatch = filePatch;
    }

    void attach(ProposedChange proposal) {
        this.proposedChange = proposal;
    }

    public UUID getId() { return id; }
    public String getPath() { return path; }
    public ProposedChangeOperation getOperation() { return operation; }
    public int getAdditions() { return additions; }
    public int getDeletions() { return deletions; }
    public String getFilePatch() { return filePatch; }
}
