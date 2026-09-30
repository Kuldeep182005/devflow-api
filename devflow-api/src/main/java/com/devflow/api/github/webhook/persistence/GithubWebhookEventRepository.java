package com.devflow.api.github.webhook.persistence;

import com.devflow.api.github.webhook.domain.GithubWebhookEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubWebhookEventRepository extends JpaRepository<GithubWebhookEvent, UUID> {

    Optional<GithubWebhookEvent> findByDeliveryId(String deliveryId);
}
