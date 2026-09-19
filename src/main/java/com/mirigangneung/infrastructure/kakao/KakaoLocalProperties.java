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
    // 강릉시 행정구역 전체(카카오 좌표→행정구역 조회로 측정). 이전 값은 위도 37.65에서 잘려
    // 안반데기·노추산·옥계 등 왕산면·옥계면 남쪽이 검색되지 않았다.
    public static final String DEFAULT_GANGNEUNG_RECT = "128.58,38.00,129.18,37.49";

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
