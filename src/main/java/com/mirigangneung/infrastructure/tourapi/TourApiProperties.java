package com.mirigangneung.infrastructure.tourapi;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="tour.api") public record TourApiProperties(String baseUrl,String key,java.time.Duration timeout) {}
