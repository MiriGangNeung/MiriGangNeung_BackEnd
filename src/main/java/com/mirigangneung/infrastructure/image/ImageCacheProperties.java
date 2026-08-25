package com.mirigangneung.infrastructure.image;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "image.cache")
public record ImageCacheProperties(
        boolean enabled,
        String storageDir,
        String publicBaseUrl,
        Duration timeout,
        long maxDownloadBytes,
        int thumbnailMaxWidth,
        Duration browserTtl) {

    public ImageCacheProperties {
        if (storageDir == null || storageDir.isBlank()) {
            storageDir = System.getProperty("java.io.tmpdir") + "/mirigangneung-images";
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = "http://localhost:8080/media/images";
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(10);
        }
        if (maxDownloadBytes <= 0) {
            maxDownloadBytes = 10 * 1024 * 1024;
        }
        if (thumbnailMaxWidth <= 0) {
            thumbnailMaxWidth = 640;
        }
        if (browserTtl == null || browserTtl.isZero() || browserTtl.isNegative()) {
            browserTtl = Duration.ofDays(365);
        }
    }
}
