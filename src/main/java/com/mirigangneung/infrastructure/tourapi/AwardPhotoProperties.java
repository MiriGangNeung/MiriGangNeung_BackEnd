package com.mirigangneung.infrastructure.tourapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tour.award")
public record AwardPhotoProperties(String baseUrl, String key, Duration timeout) {
}
