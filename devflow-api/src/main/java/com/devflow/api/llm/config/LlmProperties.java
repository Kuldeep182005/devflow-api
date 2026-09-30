package com.devflow.api.llm.config;

import com.devflow.api.llm.exception.LlmConfigurationException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private boolean enabled = false;
    private String provider = "openai-compatible";
    private String baseUrl = "";
    private String model = "";
    private String apiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(30);
    private int maxResponseBytes = 1_048_576;
    private int maxPromptCharacters = 12_000;
    private int maxOutputTokens = 2_048;
    private double defaultTemperature = 0.2;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    public int getMaxPromptCharacters() {
        return maxPromptCharacters;
    }

    public void setMaxPromptCharacters(int maxPromptCharacters) {
        this.maxPromptCharacters = maxPromptCharacters;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public double getDefaultTemperature() {
        return defaultTemperature;
    }

    public void setDefaultTemperature(double defaultTemperature) {
        this.defaultTemperature = defaultTemperature;
    }

    public void validateForUse() {
        if (!enabled) {
            return;
        }
        if (provider == null || provider.isBlank()) {
            throw new LlmConfigurationException("LLM provider must be configured when enabled");
        }
        if (!"openai-compatible".equalsIgnoreCase(provider)) {
            throw new LlmConfigurationException("Unsupported LLM provider: " + provider);
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new LlmConfigurationException("LLM baseUrl must be configured when enabled");
        }
        if (model == null || model.isBlank()) {
            throw new LlmConfigurationException("LLM model must be configured when enabled");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmConfigurationException("LLM API key must be configured when enabled");
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new LlmConfigurationException("LLM connect timeout must be positive");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new LlmConfigurationException("LLM request timeout must be positive");
        }
        if (maxResponseBytes <= 0) {
            throw new LlmConfigurationException("LLM maxResponseBytes must be positive");
        }
        if (maxPromptCharacters <= 0) {
            throw new LlmConfigurationException("LLM maxPromptCharacters must be positive");
        }
        if (maxOutputTokens <= 0) {
            throw new LlmConfigurationException("LLM maxOutputTokens must be positive");
        }
        if (defaultTemperature < 0.0 || defaultTemperature > 2.0) {
            throw new LlmConfigurationException("LLM defaultTemperature must be between 0.0 and 2.0");
        }
    }
}
