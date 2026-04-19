package com.smfmo.integration.openproject.adapters.out.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(String recipient) {}
