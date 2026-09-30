package com.devflow.api.llm;

import com.devflow.api.llm.api.LlmRequest;
import com.devflow.api.llm.api.LlmResponse;
import com.devflow.api.llm.config.LlmProperties;
import com.devflow.api.llm.exception.LlmConfigurationException;
import com.devflow.api.llm.provider.LlmProvider;
import com.devflow.api.llm.service.LlmService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmServiceTest {

    @Test
    void disabledServiceRejectsGeneration() {
        LlmService service = new LlmService(null, new LlmProperties());

        assertThatThrownBy(() -> service.complete("hello"))
                .isInstanceOf(LlmConfigurationException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void serviceUsesProviderWhenEnabled() {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setProvider("openai-compatible");
        properties.setBaseUrl("https://example.com/v1");
        properties.setModel("gpt-4o-mini");
        properties.setApiKey("test-key");
        properties.setMaxOutputTokens(1024);
        properties.setMaxPromptCharacters(12000);

        LlmProvider provider = request -> new LlmResponse(
                "stubbed response",
                request.prompt(),
                properties.getModel(),
                "stop",
                12,
                7,
                Instant.now());

        LlmService service = new LlmService(provider, properties);
        LlmResponse response = service.complete(new LlmRequest("hello there", 0.2, 64));

        assertThat(response.text()).isEqualTo("stubbed response");
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
    }
}
