package com.devflow.api.github.webhook.security;

import com.devflow.api.github.webhook.config.GithubWebhookProperties;
import com.devflow.api.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubWebhookSignatureVerifierTest {

    @Test
    void verifiesSignatureUsingConstantTimeComparisonPath() throws Exception {
        GithubWebhookProperties properties = new GithubWebhookProperties();
        properties.setSecret("test-webhook-secret");
        byte[] body = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));

        new GithubWebhookSignatureVerifier(properties).verify(signature, body);
    }

    @Test
    void rejectsMalformedAndIncorrectSignatures() {
        GithubWebhookProperties properties = new GithubWebhookProperties();
        properties.setSecret("test-webhook-secret");
        GithubWebhookSignatureVerifier verifier = new GithubWebhookSignatureVerifier(properties);

        assertThatThrownBy(() -> verifier.verify("sha256=not-hex", new byte[] {1}))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatus().value()).isEqualTo(401));
    }
}
