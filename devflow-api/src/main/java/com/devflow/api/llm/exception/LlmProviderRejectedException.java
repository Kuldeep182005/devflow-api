package com.devflow.api.llm.exception;

public class LlmProviderRejectedException extends LlmException {

    public LlmProviderRejectedException(String message) {
        super(message);
    }
}
