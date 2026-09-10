package com.mirigangneung.place.dto;

import com.mirigangneung.place.domain.Place;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PlaceResponse(
        String id,
        String name,
        String shortDescription,
        String region,
        String category,
        List<String> tags,
        String thumbnailUrl,
        Double latitude,
        Double longitude,
        List<String> imageUrls,
        List<String> originalImageUrls
) {
    private static final int MAX_IMAGE_URLS = 5;

    public PlaceResponse(String id, String name, String region, String category, List<String> tags,
                         String thumbnailUrl, Double latitude, Double longitude) {
        this(id, name, null, region, category, tags, thumbnailUrl, latitude, longitude, List.of(), List.of());
    }

    public PlaceResponse(String id, String name, String region, String category, List<String> tags,
                         String thumbnailUrl, Double latitude, Double longitude, List<String> imageUrls) {
        this(id, name, null, region, category, tags, thumbnailUrl, latitude, longitude, imageUrls, imageUrls);
    }

    public PlaceResponse {
        List<String> rawImageUrls = imageUrls;
        List<String> rawOriginalImageUrls = originalImageUrls;
        tags = tags == null ? List.of() : List.copyOf(tags);
        imageUrls = normalizeImageUrls(thumbnailUrl, imageUrls);
        originalImageUrls = normalizeOriginalImageUrls(rawImageUrls, rawOriginalImageUrls, imageUrls);
    }

    public static PlaceResponse from(Place place) {
        List<String> imageUrls = hasText(place.getThumbnailUrl()) ? List.of(place.getThumbnailUrl()) : List.of();
        return from(place, imageUrls, imageUrls);
    }

    public static PlaceResponse from(Place place, List<String> imageUrls) {
        return from(place, imageUrls, imageUrls);
    }

    public static PlaceResponse from(Place place, List<String> imageUrls, List<String> originalImageUrls) {
        String allowedThumbnailUrl = imageUrls == null ? null : imageUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(PlaceResponse::hasText)
                .findFirst()
                .orElse(null);
        return new PlaceResponse(
                place.getId().toString(),
                place.getName(),
                com.mirigangneung.place.service.PlaceShortDescriptionCatalog.get(place.getName()),
                place.getRegion(),
                place.getCategory(),
                List.of(),
                allowedThumbnailUrl,
                place.getLatitude(),
                place.getLongitude(),
                imageUrls,
                originalImageUrls
        );
    }

    private static List<String> normalizeOriginalImageUrls(
            List<String> imageUrls,
            List<String> originalImageUrls,
            List<String> normalizedImageUrls) {
        Map<String, String> originalByImage = new LinkedHashMap<>();
        if (imageUrls != null && originalImageUrls != null) {
            int pairedSize = Math.min(imageUrls.size(), originalImageUrls.size());
            for (int index = 0; index < pairedSize; index++) {
                String imageUrl = trimToNull(imageUrls.get(index));
                String originalImageUrl = trimToNull(originalImageUrls.get(index));
                if (imageUrl != null && originalImageUrl != null) {
                    originalByImage.putIfAbsent(imageUrl, originalImageUrl);
                }
            }
        }
        return normalizedImageUrls.stream()
                .map(imageUrl -> originalByImage.getOrDefault(imageUrl, imageUrl))
                .toList();
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

    private static String trimToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        return hasText(trimmed) ? trimmed : null;
    }
}
