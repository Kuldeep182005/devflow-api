-- V11__approvals.sql
-- Human Approval and Approval Audit Security Boundary

-- 1. Alter task_events constraint to support approval event types
ALTER TABLE task_events DROP CONSTRAINT task_events_event_type_check;
ALTER TABLE task_events ADD CONSTRAINT task_events_event_type_check CHECK (
    event_type IN (
        'TASK_CREATED', 'TASK_STARTED', 'TASK_APPROVAL_REQUIRED',
        'TASK_RESUMED', 'TASK_SUCCEEDED', 'TASK_FAILED', 'TASK_CANCELLED',
        'APPROVAL_REQUESTED', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED', 'APPROVAL_CANCELLED'
    )
);

-- 2. Alter sandbox_executions to track the exact proposed_change_version tested
ALTER TABLE sandbox_executions
    ADD COLUMN proposed_change_version BIGINT NOT NULL DEFAULT 0;

-- 3. Create approvals table
CREATE TABLE approvals (
    id UUID PRIMARY KEY,
    proposed_change_id UUID NOT NULL,
    task_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    repository_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    proposed_change_version BIGINT NOT NULL,
    sandbox_execution_id UUID NOT NULL,
    requested_by_user_id UUID NOT NULL,
    decided_by_user_id UUID,
    decision_reason VARCHAR(1000),
    requested_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT approvals_task_fk
        FOREIGN KEY (task_id, organization_id)
        REFERENCES tasks (id, organization_id)
        ON DELETE RESTRICT,
    CONSTRAINT approvals_repository_fk
        FOREIGN KEY (organization_id, repository_id)
        REFERENCES repositories (organization_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT approvals_proposed_change_fk
        FOREIGN KEY (proposed_change_id)
        REFERENCES proposed_changes (id)
        ON DELETE RESTRICT,
    CONSTRAINT approvals_sandbox_execution_fk
        FOREIGN KEY (sandbox_execution_id)
        REFERENCES sandbox_executions (id)
        ON DELETE RESTRICT,
    CONSTRAINT approvals_requested_by_fk
        FOREIGN KEY (requested_by_user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT approvals_decided_by_fk
        FOREIGN KEY (decided_by_user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT approvals_status_check
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT approvals_decision_state_check
        CHECK (
            (status = 'PENDING' AND decided_by_user_id IS NULL AND decided_at IS NULL)
            OR (status <> 'PENDING' AND decided_by_user_id IS NOT NULL AND decided_at IS NOT NULL)
        ),
    CONSTRAINT approvals_decision_reason_length_check
        CHECK (decision_reason IS NULL OR length(decision_reason) <= 1000)
);

-- Active pending approval uniqueness per proposed change and version
CREATE UNIQUE INDEX idx_approvals_unique_pending
    ON approvals (proposed_change_id, proposed_change_version)
    WHERE status = 'PENDING';

-- Tenant and relational query indexes
CREATE INDEX idx_approvals_organization
    ON approvals (organization_id, created_at);

CREATE INDEX idx_approvals_task
    ON approvals (task_id, created_at);

CREATE INDEX idx_approvals_proposed_change
    ON approvals (proposed_change_id, created_at);
