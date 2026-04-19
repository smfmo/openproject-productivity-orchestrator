package com.smfmo.integration.openproject.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Task(
        Long id,
        String title,
        String status,
        String assigneeId,
        LocalDate deadline,
        LocalDateTime updatedAt,
        int percentDone
) {}
