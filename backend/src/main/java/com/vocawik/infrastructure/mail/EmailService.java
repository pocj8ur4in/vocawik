package com.vocawik.infrastructure.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Sends emails via configured SMTP server. */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${mail.from-address:${spring.mail.username:}}")
    private String fromAddress;

    /**
     * Sends a plain text email.
     *
     * @param to recipient email
     * @param subject email subject
     * @param content email body
     */
    public void send(String to, String subject, String content) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("to is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException(
                    "mail.from-address or spring.mail.username is required");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress.trim());
        message.setTo(to.trim());
        message.setSubject(subject);
        message.setText(content);
        javaMailSender.send(message);
    }
}
