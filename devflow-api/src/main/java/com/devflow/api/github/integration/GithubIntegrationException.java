package com.devflow.api.github.integration;

import org.springframework.http.HttpStatus;

public class GithubIntegrationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public GithubIntegrationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
