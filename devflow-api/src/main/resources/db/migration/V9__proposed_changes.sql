CREATE TABLE proposed_changes (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    repository_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    base_revision VARCHAR(255) NOT NULL,
    patch TEXT NOT NULL,
    validation_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT proposed_changes_task_fk
        FOREIGN KEY (task_id, organization_id) REFERENCES tasks (id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT proposed_changes_repository_fk
        FOREIGN KEY (organization_id, repository_id) REFERENCES repositories (organization_id, id) ON DELETE RESTRICT,
    CONSTRAINT proposed_changes_status_check CHECK (status IN ('DRAFT', 'VALIDATED', 'INVALID')),
    CONSTRAINT proposed_changes_title_length_check CHECK (length(title) BETWEEN 1 AND 255),
    CONSTRAINT proposed_changes_description_length_check CHECK (length(description) BETWEEN 1 AND 4000),
    CONSTRAINT proposed_changes_base_revision_length_check CHECK (length(base_revision) BETWEEN 1 AND 255),
    CONSTRAINT proposed_changes_patch_length_check CHECK (length(patch) BETWEEN 1 AND 200000),
    CONSTRAINT proposed_changes_metadata_object_check CHECK (jsonb_typeof(validation_metadata) = 'object')
);

CREATE TABLE proposed_change_files (
    id UUID PRIMARY KEY,
    proposed_change_id UUID NOT NULL REFERENCES proposed_changes (id) ON DELETE CASCADE,
    path VARCHAR(1000) NOT NULL,
    operation VARCHAR(10) NOT NULL,
    additions INTEGER NOT NULL,
    deletions INTEGER NOT NULL,
    file_patch TEXT NOT NULL,
    CONSTRAINT proposed_change_files_operation_check CHECK (operation IN ('ADD', 'MODIFY', 'DELETE')),
    CONSTRAINT proposed_change_files_path_check CHECK (length(path) BETWEEN 1 AND 1000),
    CONSTRAINT proposed_change_files_counts_check CHECK (additions >= 0 AND deletions >= 0),
    CONSTRAINT proposed_change_files_patch_check CHECK (length(file_patch) BETWEEN 1 AND 100000),
    CONSTRAINT proposed_change_files_unique_path UNIQUE (proposed_change_id, path)
);

CREATE INDEX idx_proposed_changes_task ON proposed_changes (task_id, created_at);
CREATE INDEX idx_proposed_changes_organization ON proposed_changes (organization_id, created_at);
