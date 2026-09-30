ALTER TABLE repositories
    DROP CONSTRAINT IF EXISTS repositories_provider_external_id_unique;

ALTER TABLE repositories
    ADD CONSTRAINT repositories_organization_provider_external_id_unique
        UNIQUE (organization_id, provider, external_id);

ALTER TABLE repositories
    ADD CONSTRAINT repositories_organization_id_unique
        UNIQUE (organization_id, id);

ALTER TABLE tasks
    ADD CONSTRAINT tasks_organization_repository_fk
        FOREIGN KEY (organization_id, repository_id)
        REFERENCES repositories (organization_id, id)
        ON DELETE RESTRICT;
