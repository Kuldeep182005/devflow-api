package com.devflow.api.proposedchange.exception;

import com.devflow.api.web.ApiException;
import org.springframework.http.HttpStatus;

public class ProposedChangeException extends ApiException {
    public ProposedChangeException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
