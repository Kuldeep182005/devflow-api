package com.devflow.api.sandbox.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SandboxProperties.class)
public class SandboxConfiguration {
}
