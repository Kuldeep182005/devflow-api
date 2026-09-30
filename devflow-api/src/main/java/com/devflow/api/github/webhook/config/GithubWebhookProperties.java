package com.devflow.api.github.webhook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings used only to authenticate GitHub webhook requests. This secret is
 * intentionally separate from the GitHub App private key.
 */
@ConfigurationProperties(prefix = "github.webhook")
public class GithubWebhookProperties {

    private String secret = "";
    private int maxBodyBytes = 1_048_576;
    private int maxDeliveryIdLength = 255;
    private int maxEventNameLength = 100;
    private int maxSignatureLength = 71;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public int getMaxDeliveryIdLength() {
        return maxDeliveryIdLength;
    }

    public void setMaxDeliveryIdLength(int maxDeliveryIdLength) {
        this.maxDeliveryIdLength = maxDeliveryIdLength;
    }

    public int getMaxEventNameLength() {
        return maxEventNameLength;
    }

    public void setMaxEventNameLength(int maxEventNameLength) {
        this.maxEventNameLength = maxEventNameLength;
    }

    public int getMaxSignatureLength() {
        return maxSignatureLength;
    }

    public void setMaxSignatureLength(int maxSignatureLength) {
        this.maxSignatureLength = maxSignatureLength;
    }
}
