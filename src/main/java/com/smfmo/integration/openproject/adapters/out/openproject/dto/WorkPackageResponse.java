package com.smfmo.integration.openproject.adapters.out.openproject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WorkPackageResponse {

    private Long id;
    private String subject;
    private Integer percentageDone;
    private String dueDate;
    private String updatedAt;

    @JsonProperty("_links")
    private Links links;

    @Data
    public static class Links {
        private LinkItem status;
        private LinkItem assignee;
    }

    @Data
    public static class LinkItem {
        private String href;
        private String title;
    }

}
