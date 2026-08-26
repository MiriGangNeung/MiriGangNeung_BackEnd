package com.mirigangneung.infrastructure.kakao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.local")
public record KakaoLocalProperties(
        String baseUrl,
        String key,
        Duration timeout,
        int radiusMeters,
        int pageSize
) {
}
