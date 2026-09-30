package com.devflow.api.llm;

import com.devflow.api.llm.api.LlmRequest;
import com.devflow.api.llm.config.LlmConfiguration;
import com.devflow.api.llm.exception.LlmConfigurationException;
import com.devflow.api.llm.exception.LlmProviderUnavailableException;
import com.devflow.api.llm.exception.LlmTimeoutException;
import com.devflow.api.llm.provider.LlmProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LlmConfiguration.class, ObjectMapperConfiguration.class);

    @Test
    void disabledLlmDoesNotCreateProviderOrPreventContextStartup() {
        contextRunner
                .withPropertyValues("llm.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(LlmProvider.class);
                });
    }

    @Test
    void enabledValidConfigurationCreatesProvider() {
        contextRunner
                .withPropertyValues(validProperties("https://127.0.0.1:1"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LlmProvider.class);
                });
    }

    @Test
    void invalidEnabledConfigurationFailsWithoutExposingSecret() {
        contextRunner
                .withPropertyValues(
                        "llm.enabled=true",
                        "llm.provider=openai-compatible",
                        "llm.base-url=https://example.com",
                        "llm.model=",
                        "llm.api-key=super-secret")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(LlmConfigurationException.class)
                            .hasMessageNotContaining("super-secret");
                });
    }

    @Test
    void configuredConnectionFailureIsMappedAsProviderUnavailable() {
        contextRunner
                .withPropertyValues(validProperties("http://127.0.0.1:1"))
                .run(context -> {
                    LlmProvider provider = context.getBean(LlmProvider.class);

                    assertThatThrownBy(() -> provider.complete(new LlmRequest("failure", 0.2, 32)))
                            .isInstanceOf(LlmProviderUnavailableException.class);
                });
    }

    @Test
    void configuredHttpTimeoutIsMappedAsTimeout() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            contextRunner
                    .withPropertyValues(validProperties("http://127.0.0.1:" + server.getAddress().getPort()))
                    .withPropertyValues("llm.request-timeout=50ms")
                    .run(context -> {
                        LlmProvider provider = context.getBean(LlmProvider.class);

                        assertThatThrownBy(() -> provider.complete(new LlmRequest("timeout", 0.2, 32)))
                                .isInstanceOf(LlmTimeoutException.class);
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void configuredTransportRejectsOversizedResponseWithBoundedRead() throws IOException {
        byte[] oversized = "{\"choices\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.sendResponseHeaders(200, oversized.length);
            try (var output = exchange.getResponseBody()) {
                output.write(oversized);
            }
        });
        server.start();
        try {
            contextRunner
                    .withPropertyValues(validProperties("http://127.0.0.1:" + server.getAddress().getPort()))
                    .withPropertyValues("llm.max-response-bytes=8")
                    .run(context -> {
                        LlmProvider provider = context.getBean(LlmProvider.class);

                        assertThatThrownBy(() -> provider.complete(new LlmRequest("oversized", 0.2, 32)))
                                .isInstanceOf(com.devflow.api.llm.exception.LlmMalformedResponseException.class)
                                .hasMessageContaining("oversized");
                    });
        } finally {
            server.stop(0);
        }
    }

    private String[] validProperties(String baseUrl) {
        return new String[] {
                "llm.enabled=true",
                "llm.provider=openai-compatible",
                "llm.base-url=" + baseUrl + "/v1",
                "llm.model=test-model",
                "llm.api-key=test-key",
                "llm.connect-timeout=1s",
                "llm.request-timeout=1s",
                "llm.max-response-bytes=1024",
                "llm.max-prompt-characters=100",
                "llm.max-output-tokens=128",
                "llm.default-temperature=0.2"
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class ObjectMapperConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
