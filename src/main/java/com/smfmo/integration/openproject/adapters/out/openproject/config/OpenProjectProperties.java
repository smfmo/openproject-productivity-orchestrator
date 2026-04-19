package com.smfmo.integration.openproject.adapters.out.openproject.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openproject")
public record OpenProjectProperties(String baseUrl, String apiKey) {}
