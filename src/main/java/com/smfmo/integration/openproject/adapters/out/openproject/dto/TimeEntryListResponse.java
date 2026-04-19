package com.smfmo.integration.openproject.adapters.out.openproject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class TimeEntryListResponse {

    @JsonProperty("_embedded")
    private Embedded embedded;

    @Data
    public static class Embedded {
        private List<TimeEntryResponse> elements;
    }
}
