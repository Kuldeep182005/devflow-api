package com.devflow.api.github.webhook.domain;

import com.devflow.api.organization.domain.Organization;
import com.devflow.api.repository.domain.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "github_webhook_events")
public class GithubWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "delivery_id", nullable = false, unique = true, length = 255)
    private String deliveryId;

    @Column(name = "event_name", nullable = false, length = 100)
    private String eventName;

    @Column(name = "supported", nullable = false)
    private boolean supported;

    @Column(name = "installation_id")
    private Long installationId;

    @Column(name = "repository_external_id", length = 255)
    private String repositoryExternalId;

    @Column(name = "repository_full_name", length = 255)
    private String repositoryFullName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private Repository repository;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected GithubWebhookEvent() {
    }

    public GithubWebhookEvent(
            String deliveryId,
            String eventName,
            boolean supported,
            Long installationId,
            String repositoryExternalId,
            String repositoryFullName,
            Organization organization,
            Repository repository,
            JsonNode payload,
            Instant receivedAt) {
        this.deliveryId = deliveryId;
        this.eventName = eventName;
        this.supported = supported;
        this.installationId = installationId;
        this.repositoryExternalId = repositoryExternalId;
        this.repositoryFullName = repositoryFullName;
        this.organization = organization;
        this.repository = repository;
        this.payload = payload;
        this.receivedAt = receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getEventName() {
        return eventName;
    }

    public boolean isSupported() {
        return supported;
    }

    public Long getInstallationId() {
        return installationId;
    }

    public String getRepositoryExternalId() {
        return repositoryExternalId;
    }

    public String getRepositoryFullName() {
        return repositoryFullName;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Repository getRepository() {
        return repository;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
