package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.EmailConfigurationDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.EmailConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/email-configuration")
@RequiredArgsConstructor
@Tag(name = "Email Configuration Management", description = "APIs for managing email server configuration")
public class EmailConfigurationController {

    private static final Logger log = LoggerFactory.getLogger(EmailConfigurationController.class);
    private final EmailConfigurationService configurationService;

    @GetMapping
    @Operation(summary = "Get email configuration", description = "Retrieve the current enabled email configuration")
    public ResponseEntity<EmailConfigurationDTO> getConfiguration() {
        log.debug("GET /api/v1/email-configuration - Getting email configuration");
        EmailConfigurationDTO config = configurationService.getConfiguration();
        if (config == null) {
            log.debug("No email configuration found");
            return ResponseEntity.ok().build();
        }
        log.debug("Returning email configuration for host: {}, email: {}", config.getSmtpHost(), config.getEmailFrom());
        return ResponseEntity.ok(config);
    }

    @PostMapping
    @Operation(summary = "Save email configuration", description = "Save or update email configuration")
    public ResponseEntity<EmailConfigurationDTO> saveConfiguration(@RequestBody EmailConfigurationDTO dto) {
        log.info("POST /api/v1/email-configuration - Saving email configuration for host: {}, email: {}",
                dto.getSmtpHost(), dto.getEmailFrom());
        EmailConfigurationDTO saved = configurationService.saveConfiguration(dto);
        log.info("Email configuration saved successfully with ID: {}", saved.getId());
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/test")
    @Operation(summary = "Test email connection", description = "Test the email configuration by sending a test email")
    public ResponseEntity<Map<String, String>> testConnection(@RequestBody TestEmailRequest request) {
        log.info("POST /api/v1/email-configuration/test - Testing email connection to: {}", request.getTestEmail());
        try {
            configurationService.testConnection(request.getTestEmail());
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Test email sent successfully to " + request.getTestEmail());
            log.info("Email connection test completed successfully, sent to: {}", request.getTestEmail());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Email configuration not found for testing: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "No email configuration found. Please save the configuration first.");
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Email connection test failed: {}", e.getMessage(), e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Connection test failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Data
    public static class TestEmailRequest {
        private String testEmail;
    }
}
