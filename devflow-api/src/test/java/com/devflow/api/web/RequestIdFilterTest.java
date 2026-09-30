package com.devflow.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("Generates new UUID request ID when incoming header is absent")
    void generatesNewRequestIdWhenHeaderAbsent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringExecution = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            mdcDuringExecution.set(MDC.get(RequestIdFilter.MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        String generatedId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(generatedId).isNotNull().isNotBlank();
        assertThat(generatedId).isEqualTo(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
        assertThat(mdcDuringExecution.get()).isEqualTo(generatedId);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Preserves valid incoming request ID")
    void preservesValidIncomingRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "req-valid-12345_abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringExecution = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            mdcDuringExecution.set(MDC.get(RequestIdFilter.MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("req-valid-12345_abc");
        assertThat(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo("req-valid-12345_abc");
        assertThat(mdcDuringExecution.get()).isEqualTo("req-valid-12345_abc");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Replaces malformed request ID with freshly generated UUID")
    void replacesMalformedRequestIdWithGeneratedId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "invalid id with spaces & symbols <script>");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        String assignedId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(assignedId).isNotNull().isNotBlank();
        assertThat(assignedId).doesNotContain(" ", "<", ">");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Replaces oversized request ID exceeding 64 chars")
    void replacesOversizedRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        String oversized = "a".repeat(128);
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, oversized);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        String assignedId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(assignedId).isNotNull().isNotBlank();
        assertThat(assignedId).isNotEqualTo(oversized);
        assertThat(assignedId.length()).isLessThanOrEqualTo(64);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Always cleans up MDC even if filter chain throws exception")
    void cleansUpMdcOnException() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            throw new RuntimeException("Simulated chain failure");
        };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception expected) {
            // expected
        }

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
