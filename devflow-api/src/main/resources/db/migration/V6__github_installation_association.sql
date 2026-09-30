ALTER TABLE repositories
    ADD COLUMN github_installation_id BIGINT;

ALTER TABLE repositories
    ADD CONSTRAINT repositories_github_installation_id_check
        CHECK (github_installation_id IS NULL OR github_installation_id > 0);

CREATE INDEX idx_repositories_github_installation_id
    ON repositories (github_installation_id);
