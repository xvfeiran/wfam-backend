package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.AepProxyProperties;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEmailService {

    private final NotificationProperties props;
    private final AepProxyProperties aepProxyProperties;
    private final UserService userService;

    private static final String DEFAULT_EMAIL = "feiran.xu@cn.bosch.com";

    /**
     * 根据 loginName（即 AEP 的 username）查询邮箱地址。
     * AEP 启用时从接口实时查询，否则从 yml 配置读取。
     */
    public Optional<String> getEmail(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            return Optional.empty();
        }
        String trimmed = loginName.trim();

        if (aepProxyProperties.isEnabled()) {
            return resolveFromAep(trimmed);
        }

        // Mock 模式：从 yml 配置查找
        Map<String, String> userEmails = props.getUserEmails();
        if (userEmails != null && userEmails.containsKey(trimmed)) {
            return Optional.of(userEmails.get(trimmed));
        }

        log.debug("No email mapping for loginName={}, using default", trimmed);
        return Optional.of(DEFAULT_EMAIL);
    }

    private Optional<String> resolveFromAep(String username) {
        try {
            return userService.listUsers().stream()
                    .filter(u -> username.equals(u.get("loginName")))
                    .map(u -> u.get("email"))
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst();
        } catch (Exception e) {
            log.error("Failed to resolve email from AEP for loginName={}: {}", username, e.getMessage());
            return Optional.empty();
        }
    }
}
