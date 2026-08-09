package com.mirigangneung.infrastructure.tourapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tour.api.cache")
public record TourApiCacheProperties(Duration listTtl, Duration detailTtl) {
    public TourApiCacheProperties {
        if (listTtl == null || listTtl.isZero() || listTtl.isNegative()) {
            listTtl = Duration.ofMinutes(5);
        }
        if (detailTtl == null || detailTtl.isZero() || detailTtl.isNegative()) {
            detailTtl = Duration.ofHours(1);
        }
    }
}
