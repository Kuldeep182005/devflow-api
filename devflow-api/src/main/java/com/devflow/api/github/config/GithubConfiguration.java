package com.devflow.api.github.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GithubProperties.class)
public class GithubConfiguration {

    @Bean
    HttpClient githubHttpTransportClient(GithubProperties properties) {
        if (properties.getConnectTimeout() == null
                || properties.getConnectTimeout().isZero()
                || properties.getConnectTimeout().isNegative()
                || properties.getReadTimeout() == null
                || properties.getReadTimeout().isZero()
                || properties.getReadTimeout().isNegative()) {
            throw new IllegalStateException("github.app timeouts must be positive");
        }
        if (properties.getMaxResponseBytes() <= 0) {
            throw new IllegalStateException("github.app.max-response-bytes must be positive");
        }
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
