ALTER TABLE tasks
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN failure_code VARCHAR(80),
    ADD COLUMN failure_message VARCHAR(2000),
    ADD COLUMN failed_at TIMESTAMPTZ;

ALTER TABLE tasks
    ADD CONSTRAINT tasks_failure_message_length_check
        CHECK (failure_message IS NULL OR length(failure_message) <= 2000),
    ADD CONSTRAINT tasks_id_organization_unique UNIQUE (id, organization_id);

CREATE TABLE task_events (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    previous_state VARCHAR(30),
    new_state VARCHAR(30) NOT NULL,
    actor_source VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    correlation_id VARCHAR(255),
    idempotency_key VARCHAR(255),
    metadata JSONB,
    CONSTRAINT task_events_task_fk
        FOREIGN KEY (task_id, organization_id)
        REFERENCES tasks (id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT task_events_event_type_check CHECK (
        event_type IN (
            'TASK_CREATED', 'TASK_STARTED', 'TASK_APPROVAL_REQUIRED',
            'TASK_RESUMED', 'TASK_SUCCEEDED', 'TASK_FAILED', 'TASK_CANCELLED'
        )
    ),
    CONSTRAINT task_events_previous_state_check CHECK (
        previous_state IS NULL OR previous_state IN (
            'QUEUED', 'RUNNING', 'WAITING_FOR_APPROVAL', 'SUCCEEDED', 'FAILED', 'CANCELLED'
        )
    ),
    CONSTRAINT task_events_new_state_check CHECK (
        new_state IN ('QUEUED', 'RUNNING', 'WAITING_FOR_APPROVAL', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT task_events_actor_source_check CHECK (
        actor_source IN ('USER', 'WORKER', 'SYSTEM')
    ),
    CONSTRAINT task_events_actor_user_check CHECK (
        (actor_source = 'USER' AND actor_user_id IS NOT NULL)
        OR (actor_source <> 'USER' AND actor_user_id IS NULL)
    ),
    CONSTRAINT task_events_metadata_object_check CHECK (
        metadata IS NULL OR jsonb_typeof(metadata) = 'object'
    ),
    CONSTRAINT task_events_idempotency_unique UNIQUE (task_id, idempotency_key)
);

CREATE INDEX idx_task_events_task_occurred
    ON task_events (task_id, occurred_at, id);

CREATE INDEX idx_task_events_organization_occurred
    ON task_events (organization_id, occurred_at, id);
