package com.devflow.api.security;

import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnBean(JwtService.class)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectProvider<UserRepository> userRepositories;
    private final ObjectProvider<com.devflow.api.observability.metrics.DevFlowMetrics> metricsProvider;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            ObjectProvider<UserRepository> userRepositories,
            ObjectProvider<com.devflow.api.observability.metrics.DevFlowMetrics> metricsProvider) {
        this.jwtService = jwtService;
        this.userRepositories = userRepositories;
        this.metricsProvider = metricsProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();
        if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(token);
                UserRepository repository = userRepositories.getIfAvailable();
                if (repository != null) {
                    UUID userId = jwtService.userId(claims);
                    User user = repository.findById(userId).orElse(null);
                    if (user != null && user.isActive()) {
                        UserDetails principal = new AuthenticatedUser(user.getId(), user.getEmail());
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                    } else if (metricsProvider != null && metricsProvider.getIfAvailable() != null) {
                        metricsProvider.getIfAvailable().recordSecurityEvent("auth_failure", "inactive_or_missing_user");
                    }
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                if (metricsProvider != null && metricsProvider.getIfAvailable() != null) {
                    metricsProvider.getIfAvailable().recordSecurityEvent("auth_failure", "invalid_jwt");
                }
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}

