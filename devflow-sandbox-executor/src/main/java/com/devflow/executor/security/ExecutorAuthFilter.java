package com.devflow.executor.security;

import com.devflow.executor.config.ExecutorProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces authentication between DevFlow API and the isolated Sandbox Executor daemon.
 * Rejecting any unauthenticated or unauthorized callers with HTTP 401.
 */
@Component
@Order(1)
public class ExecutorAuthFilter extends OncePerRequestFilter {

    public static final String AUTH_HEADER = "X-Sandbox-Token";

    private final ExecutorProperties properties;

    public ExecutorAuthFilter(ExecutorProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Health check endpoint is unauthenticated
        if ("/api/v1/sandbox/health".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedToken = request.getHeader(AUTH_HEADER);
        if (providedToken == null || !secureEquals(providedToken, properties.getToken())) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("""
                    {"error":"UNAUTHORIZED","message":"Invalid or missing executor authentication token"}
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean secureEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
