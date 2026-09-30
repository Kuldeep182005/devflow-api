package com.devflow.api.llm.api;

import java.time.Instant;

public record LlmResponse(
        String text,
        String provider,
        String model,
        String finishReason,
        long inputTokens,
        long outputTokens,
        Instant createdAt) {

    public LlmResponse {
        text = text == null ? "" : text;
        provider = provider == null ? "unknown" : provider;
        model = model == null ? "unknown" : model;
        finishReason = finishReason == null ? "stop" : finishReason;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
