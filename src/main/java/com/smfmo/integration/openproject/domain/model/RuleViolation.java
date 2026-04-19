package com.smfmo.integration.openproject.domain.model;

public record RuleViolation(
        RuleType type,
        String message,
        Long taskId // null para violações globais (Ex: MISSING_HOURS)
) {}
