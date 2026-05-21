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
        CommonHeaders commonHeaders = CommonHeaderManager.getCommonHeaders();
        if (commonHeaders != null && commonHeaders.getUsername() != null && !commonHeaders.getUsername().isBlank()) {
            return Optional.of(commonHeaders.getUsername());
        }
        return Optional.of("system");
    }
}
