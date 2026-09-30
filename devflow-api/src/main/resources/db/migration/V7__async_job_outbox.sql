CREATE TABLE async_jobs (
    id UUID PRIMARY KEY,
    job_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(2000),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT async_jobs_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT async_jobs_attempts_check CHECK (attempts >= 0),
    CONSTRAINT async_jobs_max_attempts_check CHECK (max_attempts > 0)
);

CREATE INDEX idx_async_jobs_ready
    ON async_jobs (available_at, created_at)
    WHERE status = 'PENDING';

CREATE TABLE async_job_outbox (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE REFERENCES async_jobs (id) ON DELETE CASCADE,
    job_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT async_job_outbox_status_check
        CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT async_job_outbox_payload_object_check
        CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_async_job_outbox_ready
    ON async_job_outbox (available_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_async_job_outbox_stale_claims
    ON async_job_outbox (claimed_at)
    WHERE status = 'PUBLISHING';
