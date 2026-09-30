package com.devflow.api.llm.exception;

public class LlmProviderUnavailableException extends LlmException {

    public LlmProviderUnavailableException(String message) {
        super(message);
    }

    public LlmProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
