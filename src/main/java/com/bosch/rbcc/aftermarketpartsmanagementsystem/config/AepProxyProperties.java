package com.bosch.rbcc.aftermarketpartsmanagementsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aep.proxy")
public class AepProxyProperties {

    private boolean enabled = false;
    private String gatewayHost;
    private String appId;
    private String accessKey;
    private String userListUri = "/api/system/role/list-with-users";
}
