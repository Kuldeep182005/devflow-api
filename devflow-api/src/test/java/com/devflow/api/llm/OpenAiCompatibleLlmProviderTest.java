package com.devflow.api.llm;

import com.devflow.api.llm.api.LlmRequest;
import com.devflow.api.llm.api.LlmResponse;
import com.devflow.api.llm.config.LlmProperties;
import com.devflow.api.llm.exception.LlmConfigurationException;
import com.devflow.api.llm.exception.LlmMalformedResponseException;
import com.devflow.api.llm.exception.LlmProviderRejectedException;
import com.devflow.api.llm.exception.LlmProviderUnavailableException;
import com.devflow.api.llm.exception.LlmRateLimitException;
import com.devflow.api.llm.exception.LlmRequestSerializationException;
import com.devflow.api.llm.exception.LlmTimeoutException;
import com.devflow.api.llm.exception.LlmValidationException;
import com.devflow.api.llm.provider.LlmHttpTransport;
import com.devflow.api.llm.provider.OpenAiCompatibleLlmProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleLlmProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successfulProviderCallReturnsNormalizedResponse() {
        LlmProperties properties = validProperties();
        LlmHttpTransport transport = request -> response(200, """
                {"choices":[{"message":{"content":"hello from llm"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":7,"completion_tokens":4}}
                """);

        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties, objectMapper, transport);
        LlmResponse actual = provider.complete(new LlmRequest("Please say hello", 0.2, 128));

        assertThat(actual.text()).isEqualTo("hello from llm");
        assertThat(actual.provider()).isEqualTo("openai-compatible");
        assertThat(actual.model()).isEqualTo("gpt-4o-mini");
        assertThat(actual.outputTokens()).isEqualTo(4L);
    }

    @Test
    void requestValidationRejectsPromptLargerThanConfiguredLimit() {
        LlmProperties properties = validProperties();
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties, objectMapper, request -> response(200, "{}"));
        String prompt = "A".repeat(properties.getMaxPromptCharacters() + 1);

        assertThatThrownBy(() -> provider.complete(new LlmRequest(prompt, 0.2, 64)))
                .isInstanceOf(LlmValidationException.class)
                .hasMessageContaining("maximum prompt characters");
    }

    @Test
    void requestValidationRejectsInvalidTemperature() {
        LlmProperties properties = validProperties();
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties, objectMapper, request -> response(200, "{}"));

        assertThatThrownBy(() -> provider.complete(new LlmRequest("hi", 3.0, 64)))
                .isInstanceOf(LlmValidationException.class)
                .hasMessageContaining("0.0 and 2.0");
    }

    @Test
    void requestValidationRejectsInvalidTokenLimit() {
        LlmProperties properties = validProperties();
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties, objectMapper, request -> response(200, "{}"));

        assertThatThrownBy(() -> provider.complete(new LlmRequest("hi", 0.2, properties.getMaxOutputTokens() + 1)))
                .isInstanceOf(LlmValidationException.class)
                .hasMessageContaining("maximum output size");
    }

    @Test
    void timeoutIsNormalizedAsTypedException() {
        LlmProperties properties = validProperties();
        LlmHttpTransport transport = request -> {
            throw new HttpTimeoutException("timed out");
        };
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties, objectMapper, transport);

        assertThatThrownBy(() -> provider.complete(new LlmRequest("timeout test", 0.0, 64)))
                .isInstanceOf(LlmTimeoutException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void rateLimitIsNormalized() {
        LlmProperties properties = validProperties();
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties, objectMapper, request -> response(429, "{}"));

        assertThatThrownBy(() -> provider.complete(new LlmRequest("rate limit", 0.2, 64)))
                .isInstanceOf(LlmRateLimitException.class);
    }

    @Test
    void provider4xxIsNormalized() {
        LlmProperties properties = validProperties();
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties, objectMapper, request -> response(400, "{}"));

        assertThatThrownBy(() -> provider.complete(new LlmRequest("rejected", 0.2, 64)))
                .isInstanceOf(LlmProviderRejectedException.class);
    }

    @Test
    void provider5xxIsNormalized() {
        LlmProperties properties = validProperties();
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties, objectMapper, request -> response(503, "{}"));

        assertThatThrownBy(() -> provider.complete(new LlmRequest("unavailable", 0.2, 64)))
                .isInstanceOf(LlmProviderUnavailableException.class);
    }

    @Test
    void malformedProviderPayloadIsRejectedSafely() {
        LlmProperties properties = validProperties();
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties, objectMapper, request -> response(200, "not json"));

        assertThatThrownBy(() -> provider.complete(new LlmRequest("malformed", 0.2, 64)))
                .isInstanceOf(LlmMalformedResponseException.class);
    }

    @Test
    void oversizedProviderPayloadIsRejectedBeforeFullBuffering() {
        LlmProperties properties = validProperties();
        properties.setMaxResponseBytes(8);
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(
                properties, objectMapper, request -> response(200, "{\"choices\":[]}"));

        assertThatThrownBy(() -> provider.complete(new LlmRequest("oversized", 0.2, 64)))
                .isInstanceOf(LlmMalformedResponseException.class)
                .hasMessageContaining("oversized");
    }

    @Test
    void transportIoFailureIsNormalizedAsProviderUnavailable() {
        LlmProperties properties = validProperties();
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(
                properties, objectMapper, request -> {
                    throw new IOException("connection refused");
                });

        assertThatThrownBy(() -> provider.complete(new LlmRequest("unavailable", 0.2, 64)))
                .isInstanceOf(LlmProviderUnavailableException.class);
    }

    @Test
    void requestSerializationFailureUsesRequestSerializationException() {
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonMappingException("serialization failed");
            }
        };
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(
                validProperties(), failingMapper, request -> response(200, "{}"));

        assertThatThrownBy(() -> provider.complete(new LlmRequest("serialize", 0.2, 64)))
                .isInstanceOf(LlmRequestSerializationException.class)
                .hasMessageContaining("encode LLM request");
    }

    @Test
    void unsupportedProviderConfigurationIsRejected() {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setProvider("gemini");
        properties.setBaseUrl("https://example.com");
        properties.setModel("gemini-pro");
        properties.setApiKey("secret");

        assertThatThrownBy(properties::validateForUse)
                .isInstanceOf(LlmConfigurationException.class)
                .hasMessageContaining("Unsupported LLM provider");
    }

    private LlmProperties validProperties() {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setProvider("openai-compatible");
        properties.setBaseUrl("https://example.com/v1");
        properties.setModel("gpt-4o-mini");
        properties.setApiKey("test-key");
        properties.setRequestTimeout(java.time.Duration.ofSeconds(5));
        properties.setConnectTimeout(java.time.Duration.ofSeconds(2));
        properties.setMaxResponseBytes(1024 * 1024);
        properties.setMaxPromptCharacters(12000);
        properties.setMaxOutputTokens(512);
        properties.setDefaultTemperature(0.2);
        return properties;
    }

    private HttpResponse<InputStream> response(int statusCode, String body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return HttpRequest.newBuilder(URI.create("https://example.com/v1/chat/completions")).GET().build();
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of("content-type", List.of("application/json")), (a, b) -> true);
            }

            @Override
            public InputStream body() {
                return new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("https://example.com/v1/chat/completions");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }

        };
    }
}
