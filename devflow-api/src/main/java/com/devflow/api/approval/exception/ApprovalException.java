package com.devflow.api.approval.exception;

import com.devflow.api.web.ApiException;
import org.springframework.http.HttpStatus;

public class ApprovalException extends ApiException {

    public ApprovalException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
