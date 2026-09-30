package com.devflow.api.security;

import com.devflow.api.security.ratelimit.RateLimitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, PasswordProperties.class, RateLimitProperties.class})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilters,
            SecurityErrorHandler securityErrorHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .cacheControl(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/**", "/api/v1/health",
                                "/actuator/health", "/actuator/health/**", "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/github/webhooks").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .anyRequest().authenticated());
        JwtAuthenticationFilter jwtAuthenticationFilter = jwtAuthenticationFilters.getIfAvailable();
        if (jwtAuthenticationFilter != null) {
            http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder(PasswordProperties properties) {
        if (properties.getBcryptStrength() < 4 || properties.getBcryptStrength() > 31) {
            throw new IllegalStateException("security.password.bcrypt-strength must be between 4 and 31");
        }
        return new BCryptPasswordEncoder(properties.getBcryptStrength());
    }

    @Bean
    SecurityErrorHandler securityErrorHandler(
            ObjectMapper objectMapper,
            ObjectProvider<com.devflow.api.observability.metrics.DevFlowMetrics> metricsProvider) {
        return new SecurityErrorHandler(objectMapper, metricsProvider.getIfAvailable());
    }
}


