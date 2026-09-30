package com.devflow.api.github.webhook.service;

import com.devflow.api.async.persistence.AsyncJobStore;
import com.devflow.api.github.webhook.config.GithubWebhookProperties;
import com.devflow.api.github.webhook.domain.GithubWebhookEvent;
import com.devflow.api.github.webhook.persistence.GithubWebhookEventRepository;
import com.devflow.api.github.webhook.security.GithubWebhookSignatureVerifier;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.persistence.repository.RepositoryRepository;
import com.devflow.api.repository.domain.Repository;
import com.devflow.api.repository.domain.RepositoryProvider;
import com.devflow.api.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GithubWebhookService {

    private static final Pattern HEADER_VALUE = Pattern.compile("[A-Za-z0-9._:-]+");
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "check_run", "check_suite", "create", "delete", "deployment", "deployment_status",
            "installation", "installation_repositories", "issue_comment", "issues", "ping",
            "pull_request", "pull_request_review", "push", "release", "repository", "workflow_run");

    private final GithubWebhookEventRepository eventRepository;
    private final RepositoryRepository repositoryRepository;
    private final GithubWebhookSignatureVerifier signatureVerifier;
    private final GithubWebhookProperties properties;
    private final ObjectMapper objectMapper;
    private final AsyncJobStore asyncJobStore;

    public GithubWebhookService(
            GithubWebhookEventRepository eventRepository,
            RepositoryRepository repositoryRepository,
            GithubWebhookSignatureVerifier signatureVerifier,
            GithubWebhookProperties properties,
            ObjectMapper objectMapper,
            AsyncJobStore asyncJobStore) {
        this.eventRepository = eventRepository;
        this.repositoryRepository = repositoryRepository;
        this.signatureVerifier = signatureVerifier;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.asyncJobStore = asyncJobStore;
    }

    public int maxBodyBytes() {
        return properties.getMaxBodyBytes();
    }

    @Transactional
    public void ingest(String signature, String eventName, String deliveryId, byte[] body) {
        if (properties.getMaxBodyBytes() <= 0
                || properties.getMaxEventNameLength() <= 0
                || properties.getMaxDeliveryIdLength() <= 0) {
            throw new IllegalStateException("GitHub webhook limits must be positive");
        }
        if (body == null || body.length == 0) {
            throw invalidRequest("The webhook payload must not be empty");
        }
        if (body.length > properties.getMaxBodyBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "WEBHOOK_PAYLOAD_TOO_LARGE",
                    "The webhook payload is too large");
        }
        validateHeaders(eventName, deliveryId);
        signatureVerifier.verify(signature, body);

        JsonNode payload = parsePayload(body);
        if (eventRepository.findByDeliveryId(deliveryId).isPresent()) {
            return;
        }

        ExtractedMetadata metadata = extractMetadata(payload);
        Repository repository = resolveRepository(
                metadata.installationId(), metadata.repositoryExternalId(), metadata.repositoryFullName());
        Organization organization = repository == null ? null : repository.getOrganization();
        GithubWebhookEvent event = new GithubWebhookEvent(
                deliveryId,
                eventName,
                SUPPORTED_EVENTS.contains(eventName),
                metadata.installationId(),
                metadata.repositoryExternalId(),
                metadata.repositoryFullName(),
                organization,
                repository,
                payload,
                Instant.now());
        try {
            eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException exception) {
            // A concurrent retry of the same GitHub delivery is already persisted.
            if (eventRepository.findByDeliveryId(deliveryId).isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "WEBHOOK_PERSISTENCE_CONFLICT",
                        "The webhook could not be persisted");
            }
            return;
        }
        asyncJobStore.enqueueWebhook(event.getId());
    }

    private void validateHeaders(String eventName, String deliveryId) {
        if (eventName == null
                || eventName.isBlank()
                || eventName.length() > properties.getMaxEventNameLength()
                || !HEADER_VALUE.matcher(eventName).matches()) {
            throw invalidRequest("The GitHub event header is invalid");
        }
        if (deliveryId == null
                || deliveryId.isBlank()
                || deliveryId.length() > properties.getMaxDeliveryIdLength()
                || !HEADER_VALUE.matcher(deliveryId).matches()) {
            throw invalidRequest("The GitHub delivery header is invalid");
        }
    }

    private JsonNode parsePayload(byte[] body) {
        try {
            JsonNode payload = objectMapper.readTree(body);
            if (payload == null || !payload.isObject()) {
                throw invalidRequest("The webhook payload must be a JSON object");
            }
            return payload;
        } catch (IOException exception) {
            throw invalidRequest("The webhook payload is invalid JSON");
        }
    }

    private ExtractedMetadata extractMetadata(JsonNode payload) {
        Long installationId = null;
        JsonNode installation = payload.get("installation");
        if (installation != null && installation.isObject()) {
            JsonNode id = installation.get("id");
            if (id != null && id.isIntegralNumber() && id.longValue() > 0) {
                installationId = id.longValue();
            }
        }

        String repositoryExternalId = null;
        String repositoryFullName = null;
        JsonNode repository = payload.get("repository");
        if (repository != null && repository.isObject()) {
            JsonNode id = repository.get("id");
            if (id != null && (id.isIntegralNumber() || id.isTextual())) {
                String value = id.asText().trim();
                if (!value.isEmpty() && value.length() <= 255 && HEADER_VALUE.matcher(value).matches()) {
                    repositoryExternalId = value;
                }
            }
            JsonNode fullName = repository.get("full_name");
            if (fullName != null && fullName.isTextual()) {
                String value = fullName.asText().trim();
                if (!value.isEmpty() && value.length() <= 255) {
                    repositoryFullName = value;
                }
            }
        }
        return new ExtractedMetadata(installationId, repositoryExternalId, repositoryFullName);
    }

    private Repository resolveRepository(Long installationId, String externalId, String fullName) {
        if (installationId == null || externalId == null) {
            return null;
        }
        List<Repository> matches = repositoryRepository.findAllByProviderAndExternalId(
                RepositoryProvider.GITHUB, externalId);
        if (matches == null) {
            matches = List.of();
        }
        if (matches.size() == 1) {
            Repository candidate = matches.get(0);
            return installationId.equals(candidate.getGithubInstallationId()) ? candidate : null;
        }
        if (matches.size() > 1 && fullName != null) {
            List<Repository> named = matches.stream()
                    .filter(repository -> fullName.equalsIgnoreCase(repository.getFullName())
                            && installationId.equals(repository.getGithubInstallationId()))
                    .toList();
            if (named.size() == 1) {
                return named.get(0);
            }
        }
        return null;
    }

    private ApiException invalidRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "WEBHOOK_INVALID_REQUEST", message);
    }

    private record ExtractedMetadata(Long installationId, String repositoryExternalId, String repositoryFullName) {
    }
}
