package com.devflow.api.llm.config;

import com.devflow.api.llm.exception.LlmProviderUnavailableException;
import com.devflow.api.llm.exception.LlmTimeoutException;
import com.devflow.api.llm.provider.LlmHttpTransport;
import com.devflow.api.llm.provider.LlmProvider;
import com.devflow.api.llm.provider.OpenAiCompatibleLlmProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "llm", name = "enabled", havingValue = "true")
    LlmProvider llmProvider(LlmProperties properties, ObjectMapper objectMapper) {
        properties.validateForUse();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        LlmHttpTransport transport = request -> {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (HttpTimeoutException exception) {
                throw new LlmTimeoutException("LLM request timed out", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new LlmProviderUnavailableException("LLM request was interrupted", exception);
            } catch (IOException exception) {
                throw new LlmProviderUnavailableException("LLM provider is unavailable", exception);
            }
        };

        return new OpenAiCompatibleLlmProvider(properties, objectMapper, transport);
    }
}
