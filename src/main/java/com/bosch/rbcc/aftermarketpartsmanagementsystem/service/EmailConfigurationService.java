package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.EmailConfigurationDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.EmailConfiguration;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.EmailConfigurationRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(EmailConfigurationService.class);
    private final EmailConfigurationRepository repository;

    public EmailConfigurationDTO getConfiguration() {
        return repository.findByEnabledTrue()
                .map(this::toDTO)
                .orElse(null);
    }

    @Transactional
    public EmailConfigurationDTO saveConfiguration(EmailConfigurationDTO dto) {
        log.info("Saving email configuration for host: {}, port: {}, from: {}",
                dto.getSmtpHost(), dto.getSmtpPort(), dto.getEmailFrom());
        // 禁用所有现有配置
        repository.findAll().forEach(config -> {
            config.setEnabled(false);
            repository.save(config);
        });

        EmailConfiguration config;
        if (dto.getId() != null && !dto.getId().isBlank()) {
            config = repository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Email configuration not found: " + dto.getId()));
        } else {
            config = new EmailConfiguration();
            config.setId(UUID.randomUUID().toString());
        }

        config.setSmtpHost(dto.getSmtpHost());
        config.setSmtpPort(dto.getSmtpPort() != null ? dto.getSmtpPort() : 25);
        config.setSmtpUsername(dto.getSmtpUsername());
        config.setSmtpDomain(dto.getSmtpDomain());
        config.setEmailFrom(dto.getEmailFrom());
        config.setEmailFromDisplayName(dto.getEmailFromDisplayName());
        config.setEmailPassword(dto.getEmailPassword());
        config.setEnableSsl(dto.getEnableSsl() != null ? dto.getEnableSsl() : false);
        config.setEnabled(true);

        EmailConfiguration saved = repository.save(config);
        log.info("Email configuration saved successfully with ID: {}", saved.getId());
        return toDTO(saved);
    }

    public EmailConfigurationDTO testConnection(String testEmail) {
        EmailConfiguration config = repository.findByEnabledTrue()
                .orElseThrow(() -> new IllegalArgumentException("No email configuration found"));

        JavaMailSender testSender = createMailSender(config);

        try {
            MimeMessage message = testSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(testEmail);
            helper.setFrom(config.getFromAddress());
            helper.setSubject("Email Configuration Test");
            helper.setText("This is a test email to verify the email configuration is working correctly.\n\n" +
                    "If you receive this email, the SMTP configuration is correct.\n\n" +
                    "Configuration Details:\n" +
                    "SMTP Host: " + config.getSmtpHost() + "\n" +
                    "SMTP Port: " + config.getSmtpPort() + "\n" +
                    "From: " + config.getFromAddress());

            testSender.send(message);
            log.info("Test email sent successfully to {}", testEmail);
            return toDTO(config);
        } catch (MessagingException e) {
            log.error("Failed to send test email - Host: {}, Port: {}, To: {}, Error: {}",
                    config.getSmtpHost(), config.getSmtpPort(), testEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send test email: " + e.getMessage(), e);
        }
    }

    public JavaMailSender getMailSender() {
        EmailConfiguration config = repository.findByEnabledTrue()
                .orElseThrow(() -> new IllegalStateException("No email configuration enabled"));
        return createMailSender(config);
    }

    public boolean hasConfiguration() {
        return repository.findByEnabledTrue().isPresent();
    }

    private JavaMailSender createMailSender(EmailConfiguration config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getSmtpHost());
        sender.setPort(config.getSmtpPort());

        // 使用 smtpUsername 作为用户名，如果为空则使用 emailFrom
        String username = config.getSmtpUsername();
        if (username == null || username.isBlank()) {
            username = config.getEmailFrom();
        }
        sender.setUsername(username);
        sender.setPassword(config.getEmailPassword());

        Properties props = new Properties();
        // 基础认证设置
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ehlo", "true");

        // 增加超时时间到60秒
        props.put("mail.smtp.connectiontimeout", "60000");
        props.put("mail.smtp.timeout", "60000");
        props.put("mail.smtp.writetimeout", "60000");
        props.put("mail.smtp.sendpartial", "true");

        // 根据端口和SSL设置配置加密方式
        if (config.getEnableSsl() != null && config.getEnableSsl()) {
            // SSL加密 (端口465) - 使用 smtps 协议
            sender.setProtocol("smtps");
            props.put("mail.smtps.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "false");
            props.put("mail.smtp.ssl.trust", "*");
        } else {
            // 端口25或587都启用STARTTLS（如果服务器支持）
            // 强制要求 STARTTLS
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "false");
            props.put("mail.smtp.ssl.trust", "*");
            props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        }

        // 配置认证机制 - 简化认证机制，优先使用 LOGIN
        if (config.getSmtpDomain() != null && !config.getSmtpDomain().isBlank()) {
            // 有域配置时
            props.put("mail.smtp.auth.mechanisms", "LOGIN PLAIN");
            props.put("mail.smtp.sasl.realm", config.getSmtpDomain());
            props.put("mail.smtp.sasl.usecanonicalhostname", "false");
        } else {
            // 无域配置时
            props.put("mail.smtp.auth.mechanisms", "LOGIN PLAIN");
        }

        sender.setJavaMailProperties(props);
        return sender;
    }

    private EmailConfigurationDTO toDTO(EmailConfiguration entity) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return EmailConfigurationDTO.builder()
                .id(entity.getId())
                .smtpHost(entity.getSmtpHost())
                .smtpPort(entity.getSmtpPort())
                .smtpUsername(entity.getSmtpUsername())
                .smtpDomain(entity.getSmtpDomain())
                .emailFrom(entity.getEmailFrom())
                .emailFromDisplayName(entity.getEmailFromDisplayName())
                .emailPassword(entity.getEmailPassword())
                .enableSsl(entity.getEnableSsl())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().format(formatter) : null)
                .createdBy(entity.getCreatedBy())
                .build();
    }
}
