package com.smfmo.integration.openproject.adapters.out.notification;

import com.smfmo.integration.openproject.ports.out.NotificationPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(NotificationProperties.class)
public class EmailNotificationAdapter implements NotificationPort {

    private final JavaMailSender mailSender;
    private final NotificationProperties notificationProperties;

    public EmailNotificationAdapter(JavaMailSender mailSender, NotificationProperties notificationProperties) {
        this.mailSender = mailSender;
        this.notificationProperties = notificationProperties;
    }

    /**
     * Envia uma notificação por e-mail simples para o destinatário configurado.
     *
     * @param subject assunto do e-mail
     * @param body corpo do e-mail
     */
    @Override
    public void send(String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(notificationProperties.recipient());
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
