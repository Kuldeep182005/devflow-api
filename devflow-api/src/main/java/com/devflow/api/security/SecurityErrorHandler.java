package com.devflow.api.security;

import com.devflow.api.web.ApiError;
import com.devflow.api.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final com.devflow.api.observability.metrics.DevFlowMetrics metrics;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    public SecurityErrorHandler(ObjectMapper objectMapper, com.devflow.api.observability.metrics.DevFlowMetrics metrics) {
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        if (metrics != null) {
            metrics.recordSecurityEvent("auth_failure", "unauthorized");
        }
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, request, 401, "UNAUTHORIZED", "Authentication is required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        if (metrics != null) {
            metrics.recordSecurityEvent("access_denied", "forbidden");
        }
        write(response, request, 403, "FORBIDDEN", "Access to this resource is forbidden");
    }


    private void write(HttpServletResponse response, HttpServletRequest request, int status,
                       String code, String message) throws IOException {
        String requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) instanceof String id
                ? id : request.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ApiError(code, message, Instant.now(),
                        requestId == null || requestId.isBlank() ? "unknown" : requestId,
                        List.of()));
    }
}
