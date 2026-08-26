package com.mirigangneung.infrastructure.image;

import com.mirigangneung.place.domain.PlaceImage;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PlaceImageUrlResolver {
    private static final Pattern SAFE_STORAGE_KEY = Pattern.compile("[A-Za-z0-9._-]+");
    private final String publicBaseUrl;

    public PlaceImageUrlResolver(ImageCacheProperties properties) {
        publicBaseUrl = trimTrailingSlash(properties.publicBaseUrl());
    }

    public String thumbnailUrl(PlaceImage image) {
        return cachedUrl(image.getThumbnailStorageKey(), image.getImageUrl());
    }

    public String originalUrl(PlaceImage image) {
        return cachedUrl(image.getOriginalStorageKey(), image.getImageUrl());
    }

    public String sourceUrl(PlaceImage image) {
        return image.getImageUrl();
    }

    public String cachedUrl(String storageKey) {
        return cachedUrl(storageKey, null);
    }

    private String cachedUrl(String storageKey, String fallback) {
        if (storageKey == null || storageKey.isBlank() || !SAFE_STORAGE_KEY.matcher(storageKey).matches()) {
            return fallback;
        }
        return publicBaseUrl + "/" + storageKey;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080/media/images";
        }
        return value.replaceFirst("/+$", "");
    }
}
