package com.huy.enterprise.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai-service")
public class AiServiceProperties {
    private boolean enabled = true;
    private String baseUrl = "http://localhost:8000";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 5000;
}
