package com.devflow.api.sandbox.exception;

import com.devflow.api.web.ApiException;
import org.springframework.http.HttpStatus;

public class SandboxException extends ApiException {

    public SandboxException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
