package com.bosch.rbcc.aftermarketpartsmanagementsystem.config;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaderManager;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaders;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component("auditorProvider")
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        // 如果使用 Spring Security
        // Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // return Optional.ofNullable(auth != null ? auth.getName() : "system");

        // 简单示例，默认用 system
        CommonHeaders commonHeaders = CommonHeaderManager.getCommonHeaders();

        return Optional.of("system");
    }
}
