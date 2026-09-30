package com.devflow.api.llm.provider;

import com.devflow.api.llm.api.LlmRequest;
import com.devflow.api.llm.api.LlmResponse;

public interface LlmProvider {
    default String providerName() {
        return "unknown";
    }

    LlmResponse complete(LlmRequest request);
}
