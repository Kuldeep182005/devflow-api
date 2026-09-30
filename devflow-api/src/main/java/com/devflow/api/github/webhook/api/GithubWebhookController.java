package com.devflow.api.github.webhook.api;

import com.devflow.api.github.webhook.service.GithubWebhookService;
import com.devflow.api.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/github/webhooks")
public class GithubWebhookController {

    private final GithubWebhookService webhookService;

    public GithubWebhookController(GithubWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(name = "X-GitHub-Event", required = false) String eventName,
            @RequestHeader(name = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestBody byte[] body,
            HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        if (contentLength > 0 && contentLength > webhookService.maxBodyBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "WEBHOOK_PAYLOAD_TOO_LARGE", "The webhook payload is too large");
        }
        webhookService.ingest(signature, eventName, deliveryId, body);
        return ResponseEntity.accepted().build();
    }
}
