package com.smfmo.integration.openproject.adapters.out.openproject.dto;

import lombok.Data;

@Data
public class TimeEntryResponse {

    private Long id;
    private String hours; // ISO 8601 duration, ex: "PT2H30M"
    private String spentOn;  // "yyyy-MM-dd"

}
