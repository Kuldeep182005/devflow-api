package com.devflow.api.llm.exception;

public class LlmRateLimitException extends LlmException {

    public LlmRateLimitException(String message) {
        super(message);
    }
}
