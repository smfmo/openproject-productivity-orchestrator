package com.smfmo.integration.openproject.adapters.config;

import com.smfmo.integration.openproject.domain.service.RuleEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DomainConfig {

    @Bean
    public RuleEngine ruleEngine() {
        return new RuleEngine(Clock.systemDefaultZone());
    }
}
