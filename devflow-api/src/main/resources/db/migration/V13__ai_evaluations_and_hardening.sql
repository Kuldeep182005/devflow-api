-- V13__ai_evaluations_and_hardening.sql
-- Lightweight AI evaluation/audit records and query performance indexing

CREATE TABLE ai_evaluations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    task_id UUID NOT NULL,
    execution_id UUID NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    duration_ms BIGINT,
    iteration_count INT NOT NULL DEFAULT 0,
    tool_call_count INT NOT NULL DEFAULT 0,
    final_state VARCHAR(32) NOT NULL,
    failure_code VARCHAR(64),
    proposed_change_id UUID,
    sandbox_execution_id UUID,
    approval_id UUID,
    github_mutation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ai_evaluations_org_fk
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE CASCADE,
    CONSTRAINT ai_evaluations_task_fk
        FOREIGN KEY (task_id, organization_id)
        REFERENCES tasks (id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT ai_evaluations_proposed_change_fk
        FOREIGN KEY (proposed_change_id)
        REFERENCES proposed_changes (id)
        ON DELETE SET NULL,
    CONSTRAINT ai_evaluations_sandbox_fk
        FOREIGN KEY (sandbox_execution_id)
        REFERENCES sandbox_executions (id)
        ON DELETE SET NULL,
    CONSTRAINT ai_evaluations_approval_fk
        FOREIGN KEY (approval_id)
        REFERENCES approvals (id)
        ON DELETE SET NULL,
    CONSTRAINT ai_evaluations_mutation_fk
        FOREIGN KEY (github_mutation_id)
        REFERENCES github_mutations (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_ai_evaluations_task_id
    ON ai_evaluations (task_id);

CREATE INDEX idx_ai_evaluations_org_id
    ON ai_evaluations (organization_id, created_at);

CREATE INDEX idx_ai_evaluations_execution_id
    ON ai_evaluations (execution_id);

CREATE INDEX idx_tasks_org_status_created
    ON tasks (organization_id, status, created_at DESC);
