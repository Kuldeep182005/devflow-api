package com.devflow.api.github.webhook.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GithubWebhookProperties.class)
public class GithubWebhookConfiguration {
}
