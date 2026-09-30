package com.devflow.api.repository.api;

import com.devflow.api.repository.domain.RepositoryProvider;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

/**
 * Identifies a repository without accepting arbitrary URLs. The optional
 * installation id is useful when an account has more than one installation;
 * otherwise it is resolved from the organization account.
 */
public final class ConnectRepositoryRequest {

    @NotNull
    private RepositoryProvider provider;
    private String owner;
    @JsonAlias({"repo", "repositoryName"})
    private String name;
    @JsonAlias({"repositoryFullName", "fullName"})
    private String repository;
    @JsonAlias({"externalId"})
    private String externalRepository;
    private Long installationId;

    public ConnectRepositoryRequest() {
    }

    public ConnectRepositoryRequest(RepositoryProvider provider, String owner, String name) {
        this(provider, owner, name, null);
    }

    public ConnectRepositoryRequest(
            RepositoryProvider provider, String owner, String name, Long installationId) {
        this.provider = provider;
        this.owner = owner;
        this.name = name;
        this.installationId = installationId;
    }

    public RepositoryProvider provider() {
        return provider;
    }

    public String owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public Long installationId() {
        return installationId;
    }

    public String repository() {
        return repository;
    }

    public String externalRepository() {
        return externalRepository;
    }

    public void setProvider(RepositoryProvider provider) {
        this.provider = provider;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public void setExternalRepository(String externalRepository) {
        this.externalRepository = externalRepository;
    }

    public void setInstallationId(Long installationId) {
        this.installationId = installationId;
    }
}
