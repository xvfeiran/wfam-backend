package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    private final EmailConfigurationService configurationService;

    public EmailService(@Lazy EmailConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @Async
    public void sendEmail(String to, String subject, String content) {
        if (!configurationService.hasConfiguration()) {
            log.warn("No email configuration found, skipping email send");
            return;
        }

        try {
            JavaMailSender mailSender = configurationService.getMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    @Async
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        sendEmail(to, subject, htmlContent);
    }

    public boolean isAvailable() {
        return configurationService.hasConfiguration();
    }
}
