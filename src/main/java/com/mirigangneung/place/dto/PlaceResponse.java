package com.mirigangneung.place.dto;

import com.mirigangneung.place.domain.Place;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record PlaceResponse(
        String id,
        String name,
        String region,
        String category,
        List<String> tags,
        String thumbnailUrl,
        Double latitude,
        Double longitude,
        List<String> imageUrls
) {
    private static final int MAX_IMAGE_URLS = 5;

    public PlaceResponse(String id, String name, String region, String category, List<String> tags,
                         String thumbnailUrl, Double latitude, Double longitude) {
        this(id, name, region, category, tags, thumbnailUrl, latitude, longitude, List.of());
    }

    public PlaceResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
        imageUrls = normalizeImageUrls(thumbnailUrl, imageUrls);
    }

    public static PlaceResponse from(Place place) {
        return from(place, hasText(place.getThumbnailUrl()) ? List.of(place.getThumbnailUrl()) : List.of());
    }

    public static PlaceResponse from(Place place, List<String> imageUrls) {
        String allowedThumbnailUrl = imageUrls == null ? null : imageUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(PlaceResponse::hasText)
                .findFirst()
                .orElse(null);
        return new PlaceResponse(
                place.getId().toString(),
                place.getName(),
                place.getRegion(),
                place.getCategory(),
                List.of(),
                allowedThumbnailUrl,
                place.getLatitude(),
                place.getLongitude(),
                imageUrls
        );
    }

    private static List<String> normalizeImageUrls(String thumbnailUrl, List<String> imageUrls) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (hasText(thumbnailUrl)) {
            unique.add(thumbnailUrl.trim());
        }
        if (imageUrls != null) {
            imageUrls.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(PlaceResponse::hasText)
                    .forEach(unique::add);
        }
        return unique.stream().limit(MAX_IMAGE_URLS).toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
