package com.devflow.api.web;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        Instant timestamp,
        String requestId,
        List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) {
    }
}
