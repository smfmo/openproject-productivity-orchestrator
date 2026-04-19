package com.smfmo.integration.openproject.adapters.out.openproject.dto;

import lombok.Data;

@Data
public class CurrentUserResponse {
    private Long id;
    private String login;
    private String name;
}
