package com.bosch.rbcc.aftermarketpartsmanagementsystem.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "aep.proxy.enabled", havingValue = "true")
public class AepClientConfig {

    @Bean
    public RestClient aepRestClient(AepProxyProperties props) {
        return RestClient.builder()
                .baseUrl(props.getGatewayHost())
                .defaultHeader("x-aep-appid", props.getAppId())
                .defaultHeader("x-aep-accesskey", props.getAccessKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
