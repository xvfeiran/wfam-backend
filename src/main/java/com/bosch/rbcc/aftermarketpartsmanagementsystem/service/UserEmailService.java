package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 用户邮箱查询服务。
 * 当前实现从 application.yml 的 custom.notification.user-emails 配置读取。
 * 后续接入真实用户目录 API 时，只需修改 resolveFromApi() 方法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserEmailService {

    private final NotificationProperties props;

    private static final String DEFAULT_EMAIL = "feiran.xu@cn.bosch.com";

    /**
     * 根据 loginName 查询邮箱地址。
     * 查找顺序：yml 配置 → TODO: 用户目录 API → 统一使用默认邮箱
     */
    public Optional<String> getEmail(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            return Optional.empty();
        }
        String trimmed = loginName.trim();

        // 1. 从 yml 配置查找
        Map<String, String> userEmails = props.getUserEmails();
        if (userEmails != null && userEmails.containsKey(trimmed)) {
            return Optional.of(userEmails.get(trimmed));
        }

        // TODO: 接入用户目录 API，根据 loginName 查询真实邮箱
        // Optional<String> apiEmail = resolveFromApi(trimmed);
        // if (apiEmail.isPresent()) return apiEmail;

        log.debug("No email mapping for loginName={}, using default", trimmed);
        return Optional.of(DEFAULT_EMAIL);
    }
}
