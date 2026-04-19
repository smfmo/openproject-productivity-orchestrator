package com.smfmo.integration.openproject.domain.model;

import java.time.LocalDate;

public record TimeEntry(
        Long id,
        Long taskId,
        double hours,
        LocalDate date
) {}
