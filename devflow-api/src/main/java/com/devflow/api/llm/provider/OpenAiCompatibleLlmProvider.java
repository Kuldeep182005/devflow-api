package com.devflow.api.llm.provider;

import com.devflow.api.llm.api.LlmRequest;
import com.devflow.api.llm.api.LlmResponse;
import com.devflow.api.llm.config.LlmProperties;
import com.devflow.api.llm.exception.LlmConfigurationException;
import com.devflow.api.llm.exception.LlmException;
import com.devflow.api.llm.exception.LlmMalformedResponseException;
import com.devflow.api.llm.exception.LlmProviderRejectedException;
import com.devflow.api.llm.exception.LlmProviderUnavailableException;
import com.devflow.api.llm.exception.LlmRateLimitException;
import com.devflow.api.llm.exception.LlmTimeoutException;
import com.devflow.api.llm.exception.LlmValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSession;

public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private static final Pattern HTTP_SCHEME = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE);

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final LlmHttpTransport transport;

    public OpenAiCompatibleLlmProvider(LlmProperties properties, ObjectMapper objectMapper, LlmHttpTransport transport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    @Override
    public String providerName() {
        return "openai-compatible";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        LlmRequest normalized = request.withDefaults(properties);
        validateRequest(normalized);

        String endpoint = resolveChatCompletionsUri();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("messages", List.of(Map.of("role", "user", "content", normalized.prompt())));
        payload.put("temperature", normalized.temperature());
        payload.put("max_tokens", normalized.maxOutputTokens());

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                .build();

        try {
            HttpResponse<InputStream> response = transport.send(httpRequest);
            int status = response.statusCode();
            if (status == 429) {
                throw new LlmRateLimitException("LLM provider rate limit reached");
            }
            if (status == 408) {
                throw new LlmTimeoutException("LLM request timed out");
            }
            if (status == 401 || status == 403) {
                throw new LlmProviderRejectedException("LLM provider rejected the request");
            }
            if (status >= 400 && status < 500) {
                throw new LlmProviderRejectedException("LLM provider rejected the request");
            }
            if (status >= 500) {
                throw new LlmProviderUnavailableException("LLM provider is unavailable");
            }
            if (status < 200 || status >= 300) {
                throw new LlmProviderUnavailableException("LLM provider returned an unexpected status");
            }
            byte[] body = readBounded(response.body());
            if (body.length == 0) {
                throw new LlmMalformedResponseException("LLM provider returned an empty or oversized response");
            }

            JsonNode root;
            try {
                root = objectMapper.readTree(body);
            } catch (IOException exception) {
                throw new LlmMalformedResponseException("LLM provider returned malformed JSON", exception);
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new LlmMalformedResponseException("LLM provider returned no choices");
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.path("message");
            String output = message.path("content").asText();
            if (output == null || output.isBlank()) {
                throw new LlmMalformedResponseException("LLM provider returned an empty content field");
            }

            JsonNode usage = root.path("usage");
            long inputTokens = usage.path("prompt_tokens").asLong(0L);
            long outputTokens = usage.path("completion_tokens").asLong(0L);
            String finishReason = firstChoice.path("finish_reason").asText("stop");
            return new LlmResponse(output, providerName(), properties.getModel(), finishReason, inputTokens, outputTokens, Instant.now());
        } catch (LlmException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new LlmTimeoutException("LLM request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmProviderUnavailableException("LLM request was interrupted");
        } catch (IOException exception) {
            throw new LlmProviderUnavailableException("LLM provider is unavailable", exception);
        }
    }

    public void validateRequest(LlmRequest request) {
        if (request == null) {
            throw new LlmValidationException("LLM request must not be null");
        }
        if (request.prompt().length() > properties.getMaxPromptCharacters()) {
            throw new LlmValidationException("LLM prompt exceeds the configured maximum prompt characters");
        }
        if (request.maxOutputTokens() == null || request.maxOutputTokens() <= 0) {
            throw new LlmValidationException("LLM maxOutputTokens must be positive");
        }
        if (request.maxOutputTokens() > properties.getMaxOutputTokens()) {
            throw new LlmValidationException("LLM maxOutputTokens exceeds the configured maximum output size");
        }
        if (request.temperature() < 0.0 || request.temperature() > 2.0) {
            throw new LlmValidationException("LLM temperature must be between 0.0 and 2.0");
        }
    }

    private String resolveChatCompletionsUri() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new LlmConfigurationException("LLM base URL is required when LLM is enabled");
        }
        if (!HTTP_SCHEME.matcher(baseUrl).matches()) {
            throw new LlmConfigurationException("LLM base URL must be an absolute HTTP(S) URL");
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized + "/chat/completions";
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException exception) {
            throw new com.devflow.api.llm.exception.LlmRequestSerializationException(
                    "Failed to encode LLM request payload", exception);
        }
    }

    private byte[] readBounded(InputStream stream) throws IOException {
        if (stream == null) {
            throw new LlmMalformedResponseException("LLM provider returned an empty response body");
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int limit = properties.getMaxResponseBytes();
            byte[] buffer = new byte[Math.min(8192, limit)];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new LlmMalformedResponseException("LLM provider returned an oversized response");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
