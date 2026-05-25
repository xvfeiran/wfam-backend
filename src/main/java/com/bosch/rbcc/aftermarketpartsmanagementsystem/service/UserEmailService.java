package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.AepProxyProperties;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEmailService {

    private final NotificationProperties props;
    private final AepProxyProperties aepProxyProperties;
    private final UserService userService;

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

        log.warn("No email mapping for loginName={}", trimmed);
        return Optional.empty();
    }

    /**
     * 获取所有 QMC Leader 的邮箱列表。
     * <ul>
     *   <li>AEP 启用（生产）：实时查询 AEP 角色成员。</li>
     *   <li>AEP 未启用 + {@code mock-qmc-leader-enabled=true}（本地调试）：读取 {@code mock-qmc-leader-emails}。</li>
     *   <li>其他情况：返回空列表并打印警告。</li>
     * </ul>
     */
    public List<String> getQmcLeaderEmails() {
        if (aepProxyProperties.isEnabled()) {
            try {
                return userService.listQmcLeaders().stream()
                        .map(u -> u.get("email"))
                        .filter(e -> e != null && !e.isBlank())
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Failed to fetch QMC leader emails from AEP: {}", e.getMessage());
                return List.of();
            }
        }
        // AEP 未启用：仅当 debug 开关打开时才使用 mock 邮箱列表
        if (!props.isMockQmcLeaderEnabled()) {
            log.warn("QMC leader emails unavailable: AEP is disabled and mock-qmc-leader-enabled=false. "
                    + "Set custom.notification.mock-qmc-leader-enabled=true for local debug.");
            return List.of();
        }
        String emails = props.getMockQmcLeaderEmails();
        if (emails == null || emails.isBlank()) {
            log.warn("mock-qmc-leader-enabled=true but mock-qmc-leader-emails is empty.");
            return List.of();
        }
        return Arrays.stream(emails.split(","))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toList());
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
