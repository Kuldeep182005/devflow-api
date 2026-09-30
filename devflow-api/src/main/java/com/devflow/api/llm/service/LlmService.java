package com.devflow.api.llm.service;

import com.devflow.api.llm.api.LlmRequest;
import com.devflow.api.llm.api.LlmResponse;
import com.devflow.api.llm.config.LlmProperties;
import com.devflow.api.llm.exception.LlmConfigurationException;
import com.devflow.api.llm.provider.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LlmService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmService.class);

    private final LlmProvider provider;
    private final LlmProperties properties;

    public LlmService(@Autowired(required = false) LlmProvider provider, LlmProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    public LlmResponse complete(String prompt) {
        return complete(new LlmRequest(prompt, null, null));
    }

    public LlmResponse complete(LlmRequest request) {
        if (provider == null || !properties.isEnabled()) {
            throw new LlmConfigurationException("LLM is disabled or not configured");
        }

        LlmRequest normalized = request.withDefaults(properties);
        String requestId = MDC.get("requestId");
        LOGGER.debug("Generating LLM response with provider={}, model={}, promptLength={}, requestId={}",
                provider.providerName(), properties.getModel(), normalized.prompt().length(), requestId);

        return provider.complete(normalized);
    }
}
