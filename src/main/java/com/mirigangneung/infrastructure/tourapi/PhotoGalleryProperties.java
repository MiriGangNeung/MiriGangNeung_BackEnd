package com.mirigangneung.infrastructure.tourapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tour.photo-gallery")
public record PhotoGalleryProperties(String baseUrl, String key, Duration timeout) {
}
