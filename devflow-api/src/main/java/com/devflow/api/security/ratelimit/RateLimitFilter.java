package com.devflow.api.security.ratelimit;

import com.devflow.api.observability.metrics.DevFlowMetrics;
import com.devflow.api.security.AuthenticatedUser;
import com.devflow.api.web.ApiError;
import com.devflow.api.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final DevFlowMetrics metrics;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public RateLimitFilter(
            org.springframework.beans.factory.ObjectProvider<RateLimiter> rateLimiterProvider,
            org.springframework.beans.factory.ObjectProvider<RateLimitProperties> propertiesProvider,
            org.springframework.beans.factory.ObjectProvider<DevFlowMetrics> metricsProvider,
            ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiterProvider != null ? rateLimiterProvider.getIfAvailable() : null;
        this.properties = propertiesProvider != null && propertiesProvider.getIfAvailable() != null
                ? propertiesProvider.getIfAvailable() : new RateLimitProperties();
        this.metrics = metricsProvider != null ? metricsProvider.getIfAvailable() : null;
        this.objectMapper = objectMapper;
    }

    public RateLimitFilter(
            RateLimiter rateLimiter,
            RateLimitProperties properties,
            DevFlowMetrics metrics,
            ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties != null ? properties : new RateLimitProperties();
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (rateLimiter == null || !properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip actuator health/probes
        if (path.startsWith("/actuator/health") || path.equals("/api/v1/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        TierConfig tier = resolveTier(path, method);
        if (tier == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = resolveClientKey(request);
        RateLimitResult result = rateLimiter.tryAcquire(tier.name(), clientKey, tier.limit(), tier.windowSeconds());

        if (!result.allowed()) {
            if (metrics != null) {
                metrics.recordSecurityEvent("rate_limited", tier.name());
            }
            reject(request, response, result.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private TierConfig resolveTier(String path, String method) {
        if ("POST".equalsIgnoreCase(method)) {
            if ("/api/v1/auth/login".equals(path)) {
                return new TierConfig("auth-login", properties.getLoginLimit(), properties.getLoginWindowSeconds());
            }
            if ("/api/v1/auth/register".equals(path)) {
                return new TierConfig("auth-register", properties.getRegisterLimit(), properties.getRegisterWindowSeconds());
            }
            if ("/api/v1/github/webhooks".equals(path)) {
                return new TierConfig("webhooks", properties.getWebhookLimit(), properties.getWebhookWindowSeconds());
            }
            if (path.startsWith("/api/v1/organizations/") && path.endsWith("/tasks")) {
                return new TierConfig("tasks-create", properties.getTaskCreateLimit(), properties.getTaskCreateWindowSeconds());
            }
            if (path.startsWith("/api/v1/organizations/") && path.endsWith("/execute")) {
                return new TierConfig("approvals-execute", properties.getApprovalExecuteLimit(), properties.getApprovalExecuteWindowSeconds());
            }
        }
        if (path.startsWith("/api/v1/")) {
            return new TierConfig("global", properties.getGlobalLimit(), properties.getGlobalWindowSeconds());
        }
        return null;
    }

    private String resolveClientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return "user-" + user.id();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIndex = forwarded.indexOf(',');
            return (commaIndex > 0 ? forwarded.substring(0, commaIndex) : forwarded).trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null && !remote.isBlank() ? remote : "unknown";
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        }

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError error = new ApiError(
                "RATE_LIMIT_EXCEEDED",
                "Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.",
                Instant.now(),
                requestId != null ? requestId : "unknown",
                List.of());
        objectMapper.writeValue(response.getOutputStream(), error);
    }

    private record TierConfig(String name, int limit, int windowSeconds) {
    }
}
