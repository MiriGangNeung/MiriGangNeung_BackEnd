package com.mirigangneung.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiGenerationProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        Duration pollDelay) {

    public AiGenerationProperties {
        connectTimeout = positiveOrDefault(connectTimeout, Duration.ofSeconds(5));
        readTimeout = positiveOrDefault(readTimeout, Duration.ofSeconds(30));
        pollDelay = positiveOrDefault(pollDelay, Duration.ofSeconds(2));
    }

    public boolean configured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    private static Duration positiveOrDefault(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
