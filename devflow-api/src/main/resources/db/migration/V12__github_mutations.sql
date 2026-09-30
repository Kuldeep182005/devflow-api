-- V12__github_mutations.sql
-- Controlled GitHub Mutation Pipeline

-- 1. Alter task_events constraint to support GitHub mutation event types
ALTER TABLE task_events DROP CONSTRAINT task_events_event_type_check;
ALTER TABLE task_events ADD CONSTRAINT task_events_event_type_check CHECK (
    event_type IN (
        'TASK_CREATED', 'TASK_STARTED', 'TASK_APPROVAL_REQUIRED',
        'TASK_RESUMED', 'TASK_SUCCEEDED', 'TASK_FAILED', 'TASK_CANCELLED',
        'APPROVAL_REQUESTED', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED', 'APPROVAL_CANCELLED',
        'GITHUB_BRANCH_CREATED', 'GITHUB_COMMIT_CREATED', 'GITHUB_PR_CREATED', 'GITHUB_MUTATION_FAILED'
    )
);

-- 2. Create github_mutations table
CREATE TABLE github_mutations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    repository_id UUID NOT NULL,
    task_id UUID NOT NULL,
    proposed_change_id UUID NOT NULL,
    proposed_change_version BIGINT NOT NULL,
    approval_id UUID NOT NULL,
    sandbox_execution_id UUID NOT NULL,
    base_revision VARCHAR(255) NOT NULL,
    branch_name VARCHAR(255) NOT NULL,
    commit_sha VARCHAR(255),
    pull_request_number BIGINT,
    pull_request_url VARCHAR(1000),
    status VARCHAR(30) NOT NULL,
    failure_code VARCHAR(100),
    failure_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT github_mutations_task_fk
        FOREIGN KEY (task_id, organization_id)
        REFERENCES tasks (id, organization_id)
        ON DELETE RESTRICT,
    CONSTRAINT github_mutations_repository_fk
        FOREIGN KEY (organization_id, repository_id)
        REFERENCES repositories (organization_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT github_mutations_proposed_change_fk
        FOREIGN KEY (proposed_change_id)
        REFERENCES proposed_changes (id)
        ON DELETE RESTRICT,
    CONSTRAINT github_mutations_approval_fk
        FOREIGN KEY (approval_id)
        REFERENCES approvals (id)
        ON DELETE RESTRICT,
    CONSTRAINT github_mutations_sandbox_execution_fk
        FOREIGN KEY (sandbox_execution_id)
        REFERENCES sandbox_executions (id)
        ON DELETE RESTRICT,
    CONSTRAINT github_mutations_unique_proposal_version
        UNIQUE (organization_id, repository_id, proposed_change_id, proposed_change_version),
    CONSTRAINT github_mutations_status_check
        CHECK (status IN ('QUEUED', 'RUNNING', 'BRANCH_CREATED', 'COMMITTED', 'PR_CREATED', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_github_mutations_org_status
    ON github_mutations (organization_id, status);

CREATE INDEX idx_github_mutations_approval_id
    ON github_mutations (approval_id);

CREATE INDEX idx_github_mutations_task_id
    ON github_mutations (task_id);
