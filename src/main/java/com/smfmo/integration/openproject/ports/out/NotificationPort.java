package com.smfmo.integration.openproject.ports.out;

public interface NotificationPort {
    void send(String subject, String body);
}
