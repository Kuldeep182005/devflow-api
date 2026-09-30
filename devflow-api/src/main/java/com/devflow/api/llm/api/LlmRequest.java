package com.devflow.api.llm.api;

import com.devflow.api.llm.config.LlmProperties;
import com.devflow.api.llm.exception.LlmValidationException;
import java.util.Objects;

public record LlmRequest(
        String prompt,
        Double temperature,
        Integer maxOutputTokens) {

    public LlmRequest {
        prompt = prompt == null ? "" : prompt.strip();
        if (prompt.isBlank()) {
            throw new LlmValidationException("LLM prompt must not be blank");
        }
        if (temperature != null && (temperature.isNaN() || temperature.isInfinite() || temperature < 0.0 || temperature > 2.0)) {
            throw new LlmValidationException("LLM temperature must be between 0.0 and 2.0");
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new LlmValidationException("LLM maxOutputTokens must be positive");
        }
    }

    public LlmRequest withDefaults(LlmProperties properties) {
        Objects.requireNonNull(properties, "properties");
        return new LlmRequest(
                prompt,
                temperature == null ? properties.getDefaultTemperature() : temperature,
                maxOutputTokens == null ? properties.getMaxOutputTokens() : maxOutputTokens);
    }
}
