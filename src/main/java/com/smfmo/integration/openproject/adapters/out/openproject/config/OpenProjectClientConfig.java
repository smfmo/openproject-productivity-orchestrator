package com.smfmo.integration.openproject.adapters.out.openproject.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Configuration
@EnableConfigurationProperties(OpenProjectProperties.class)
public class OpenProjectClientConfig {

    @Bean
    public RestClient openProjectRestClient(OpenProjectProperties properties) {
        String credentials = Base64.getEncoder().encodeToString(("apikey:" + properties.apiKey()).getBytes());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
