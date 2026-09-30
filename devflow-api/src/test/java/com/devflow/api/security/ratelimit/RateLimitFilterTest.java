package com.devflow.api.security.ratelimit;

import com.devflow.api.observability.metrics.DevFlowMetrics;
import com.devflow.api.web.ApiError;
import com.devflow.api.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private RateLimiter rateLimiter;
    private RateLimitProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private DevFlowMetrics metrics;
    private ObjectMapper objectMapper;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        rateLimiter = Mockito.mock(RateLimiter.class);
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        meterRegistry = new SimpleMeterRegistry();
        metrics = new DevFlowMetrics(meterRegistry);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        filter = new RateLimitFilter(rateLimiter, properties, metrics, objectMapper);
    }

    @Test
    @DisplayName("Allows request when rate limiter permits")
    void allowsRequestWhenWithinLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        when(rateLimiter.tryAcquire(eq("auth-login"), eq("10.0.0.1"), anyInt(), anyInt()))
                .thenReturn(RateLimitResult.allow());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Rejects request with 429, sets Retry-After, writes ApiError JSON and records metric when limit exceeded")
    void rejectsRequestWhenLimitExceeded() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.168.1.100");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-rl-429");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        when(rateLimiter.tryAcquire(eq("auth-login"), eq("192.168.1.100"), anyInt(), anyInt()))
                .thenReturn(RateLimitResult.reject(45));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("45");

        ApiError error = objectMapper.readValue(response.getContentAsString(), ApiError.class);
        assertThat(error.code()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(error.requestId()).isEqualTo("req-rl-429");
        assertThat(meterRegistry.find("devflow.security.events").tag("event_type", "rate_limited").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Bypasses rate limiting for health endpoints")
    void bypassesHealthEndpoints() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, never()).tryAcquire(anyString(), anyString(), anyInt(), anyInt());
    }
}
