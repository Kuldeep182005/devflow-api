package com.devflow.api.github.webhook.security;

import com.devflow.api.github.webhook.config.GithubWebhookProperties;
import com.devflow.api.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GithubWebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";
    private static final int DIGEST_HEX_LENGTH = 64;

    private final GithubWebhookProperties properties;

    public GithubWebhookSignatureVerifier(GithubWebhookProperties properties) {
        this.properties = properties;
    }

    public void verify(String signature, byte[] body) {
        if (signature == null
                || signature.length() > properties.getMaxSignatureLength()
                || !signature.startsWith(PREFIX)
                || signature.length() != PREFIX.length() + DIGEST_HEX_LENGTH
                || properties.getSecret() == null
                || properties.getSecret().isBlank()) {
            throw invalidSignature();
        }

        byte[] provided = decodeHex(signature.substring(PREFIX.length()));
        byte[] expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            expected = mac.doFinal(body);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }

        if (!MessageDigest.isEqual(expected, provided)) {
            throw invalidSignature();
        }
    }

    private byte[] decodeHex(String value) {
        byte[] decoded = new byte[DIGEST_HEX_LENGTH / 2];
        for (int index = 0; index < decoded.length; index++) {
            int high = Character.digit(value.charAt(index * 2), 16);
            int low = Character.digit(value.charAt(index * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw invalidSignature();
            }
            decoded[index] = (byte) ((high << 4) | low);
        }
        return decoded;
    }

    private ApiException invalidSignature() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
                "The webhook signature is invalid");
    }
}
