package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.SmbConfigurationDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.SmbConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/smb-configuration")
@RequiredArgsConstructor
@Tag(name = "SMB Configuration Management",
        description = "APIs for managing SMB storage configuration")
public class SmbConfigurationController {

    private static final Logger log =
            LoggerFactory.getLogger(SmbConfigurationController.class);
    private final SmbConfigurationService smbConfigurationService;

    @GetMapping
    @Operation(summary = "获取SMB配置（密码脱敏）")
    public ResponseEntity<SmbConfigurationDTO> getConfiguration() {
        SmbConfigurationDTO config = smbConfigurationService.getConfiguration();
        if (config == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(config);
    }

    @PostMapping
    @Operation(summary = "保存SMB配置并热重载连接池")
    public ResponseEntity<SmbConfigurationDTO> saveConfiguration(
            @RequestBody SmbConfigurationDTO dto) {
        log.info("Saving SMB configuration for host: {}", dto.getHost());
        SmbConfigurationDTO saved = smbConfigurationService.saveConfiguration(dto);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/test")
    @Operation(summary = "测试SMB连接（使用当前DB配置）")
    public ResponseEntity<Map<String, String>> testConnection() {
        log.info("Testing SMB connection");
        try {
            smbConfigurationService.testConnection();
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "SMB连接测试成功");
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("SMB connection test failed: {}", e.getMessage(), e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "SMB连接测试失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
