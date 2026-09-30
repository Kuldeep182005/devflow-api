package com.devflow.api.web;

import com.devflow.api.auth.service.EmailAlreadyRegisteredException;
import com.devflow.api.auth.service.InvalidCredentialsException;
import com.devflow.api.llm.exception.LlmException;
import com.devflow.api.security.ratelimit.RateLimitExceededException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Handles EmailAlreadyRegisteredException with HTTP 409")
    void handlesDuplicateEmail() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-123");

        ResponseEntity<ApiError> response = handler.handleDuplicateEmail(
                new EmailAlreadyRegisteredException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(response.getBody().message()).isEqualTo("An account with this email already exists");
        assertThat(response.getBody().requestId()).isEqualTo("req-123");
    }

    @Test
    @DisplayName("Handles InvalidCredentialsException with HTTP 401")
    void handlesInvalidCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-401");

        ResponseEntity<ApiError> response = handler.handleInvalidCredentials(
                new InvalidCredentialsException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(response.getBody().requestId()).isEqualTo("req-401");
    }

    @Test
    @DisplayName("Handles AccessDeniedException with HTTP 403")
    void handlesAccessDenied() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-403");

        ResponseEntity<ApiError> response = handler.handleAccessDenied(
                new AccessDeniedException("Forbidden"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("Access to this resource is forbidden");
        assertThat(response.getBody().requestId()).isEqualTo("req-403");
    }

    @Test
    @DisplayName("Handles RateLimitExceededException with HTTP 429 and Retry-After header")
    void handlesRateLimitExceeded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-429");

        ResponseEntity<ApiError> response = handler.handleRateLimit(
                new RateLimitExceededException("Too many requests", 30), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(response.getBody().requestId()).isEqualTo("req-429");
    }

    @Test
    @DisplayName("Handles LlmException with HTTP 503 and safe generic message")
    void handlesLlmException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-503");

        ResponseEntity<ApiError> response = handler.handleLlmException(
                new LlmException("Sensitive provider connection string timeout"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(response.getBody().message()).isEqualTo("The AI language model provider is currently unavailable");
        assertThat(response.getBody().message()).doesNotContain("connection string");
        assertThat(response.getBody().requestId()).isEqualTo("req-503");
    }

    @Test
    @DisplayName("Handles unexpected Exception with HTTP 500 and safe sanitized message without stack trace leakage")
    void handlesUnexpectedException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-500");

        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new NullPointerException("Null reference at secret internal pointer"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().message()).doesNotContain("Null reference", "secret", "pointer");
        assertThat(response.getBody().requestId()).isEqualTo("req-500");
    }
}
