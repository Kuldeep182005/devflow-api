CREATE TABLE github_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id VARCHAR(255) NOT NULL,
    event_name VARCHAR(100) NOT NULL,
    supported BOOLEAN NOT NULL,
    installation_id BIGINT,
    repository_external_id VARCHAR(255),
    repository_full_name VARCHAR(255),
    organization_id UUID,
    repository_id UUID,
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT github_webhook_events_delivery_id_unique UNIQUE (delivery_id),
    CONSTRAINT github_webhook_events_delivery_id_check
        CHECK (char_length(delivery_id) BETWEEN 1 AND 255),
    CONSTRAINT github_webhook_events_event_name_check
        CHECK (char_length(event_name) BETWEEN 1 AND 100),
    CONSTRAINT github_webhook_events_installation_id_check
        CHECK (installation_id IS NULL OR installation_id > 0),
    CONSTRAINT github_webhook_events_payload_object_check
        CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT github_webhook_events_repository_fk
        FOREIGN KEY (organization_id, repository_id)
        REFERENCES repositories (organization_id, id)
        ON DELETE SET NULL
);
