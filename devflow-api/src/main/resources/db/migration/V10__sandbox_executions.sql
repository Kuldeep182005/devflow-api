CREATE TABLE sandbox_executions (
    id UUID PRIMARY KEY,
    proposed_change_id UUID NOT NULL,
    task_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    repository_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    profile VARCHAR(50) NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    duration BIGINT,
    exit_code INTEGER,
    stdout VARCHAR(20000),
    stderr VARCHAR(20000),
    output_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    failure_code VARCHAR(100),
    failure_message VARCHAR(4000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT sandbox_executions_proposed_change_fk
        FOREIGN KEY (proposed_change_id) REFERENCES proposed_changes (id) ON DELETE RESTRICT,
    CONSTRAINT sandbox_executions_task_fk
        FOREIGN KEY (task_id, organization_id) REFERENCES tasks (id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT sandbox_executions_repository_fk
        FOREIGN KEY (organization_id, repository_id) REFERENCES repositories (organization_id, id) ON DELETE RESTRICT,
    CONSTRAINT sandbox_executions_status_check
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'CANCELLED', 'RESOURCE_LIMIT', 'EXECUTOR_UNAVAILABLE')),
    CONSTRAINT sandbox_executions_profile_check
        CHECK (profile IN ('JAVA_MAVEN_TEST')),
    CONSTRAINT sandbox_executions_duration_check
        CHECK (duration IS NULL OR duration >= 0),
    CONSTRAINT sandbox_executions_stdout_length_check
        CHECK (stdout IS NULL OR length(stdout) <= 20000),
    CONSTRAINT sandbox_executions_stderr_length_check
        CHECK (stderr IS NULL OR length(stderr) <= 20000),
    CONSTRAINT sandbox_executions_failure_message_length_check
        CHECK (failure_message IS NULL OR length(failure_message) <= 4000)
);

CREATE INDEX idx_sandbox_executions_proposed_change
    ON sandbox_executions (proposed_change_id, created_at);

CREATE INDEX idx_sandbox_executions_task
    ON sandbox_executions (task_id, created_at);

CREATE INDEX idx_sandbox_executions_org_status
    ON sandbox_executions (organization_id, status);
