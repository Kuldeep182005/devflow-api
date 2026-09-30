package com.devflow.api.llm.exception;

public class LlmMalformedResponseException extends LlmException {

    public LlmMalformedResponseException(String message) {
        super(message);
    }

    public LlmMalformedResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
