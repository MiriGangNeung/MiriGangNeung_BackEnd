package com.mirigangneung.infrastructure.kakao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "kakao.local")
public record KakaoLocalProperties(
        String baseUrl,
        String key,
        Duration timeout,
        int radiusMeters,
        int pageSize,
        String allSearchRect
) {
    public static final String DEFAULT_GANGNEUNG_RECT = "128.70,37.95,129.05,37.65";

    public KakaoLocalProperties(
            String baseUrl,
            String key,
            Duration timeout,
            int radiusMeters,
            int pageSize
    ) {
        this(baseUrl, key, timeout, radiusMeters, pageSize, DEFAULT_GANGNEUNG_RECT);
    }

    @ConstructorBinding
    public KakaoLocalProperties {
        allSearchRect = allSearchRect == null || allSearchRect.isBlank()
                ? DEFAULT_GANGNEUNG_RECT
                : allSearchRect.trim();
    }
}
