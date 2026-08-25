package com.mirigangneung.place.dto;

import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.infrastructure.image.PlaceImageUrlResolver;

import java.util.List;

public record PlaceDetailResponse(String id, String name, String region, String category, String description,
                                  List<String> imageUrls, List<String> originalImageUrls,
                                  Double latitude, Double longitude,
                                  List<PlaceImageResponse> images) {
    public PlaceDetailResponse(String id, String name, String region, String category, String description,
                               List<String> imageUrls, Double latitude, Double longitude) {
        this(id, name, region, category, description, imageUrls, imageUrls, latitude, longitude,
                imageUrls == null ? List.of() : imageUrls.stream()
                        .map(url -> new PlaceImageResponse(url, null, null, null, null))
                        .toList());
    }

    public PlaceDetailResponse(String id, String name, String region, String category, String description,
                               List<String> imageUrls, Double latitude, Double longitude,
                               List<PlaceImageResponse> images) {
        this(id, name, region, category, description, imageUrls, imageUrls,
                latitude, longitude, images);
    }

    public static PlaceDetailResponse from(Place place, List<String> imageUrls) {
        return from(place, imageUrls, imageUrls);
    }

    public static PlaceDetailResponse from(Place place, List<String> imageUrls, List<String> originalImageUrls) {
        return new PlaceDetailResponse(place.getId().toString(), place.getName(), place.getRegion(),
                place.getCategory(), place.getDescription(), imageUrls, originalImageUrls,
                place.getLatitude(), place.getLongitude(),
                imageUrls.stream().map(url -> new PlaceImageResponse(url, null, null, null, null)).toList());
    }

    public static PlaceDetailResponse fromImages(Place place, List<PlaceImage> imageEntities) {
        return fromImages(place, imageEntities, null);
    }

    public static PlaceDetailResponse fromImages(
            Place place, List<PlaceImage> imageEntities, PlaceImageUrlResolver resolver) {
        List<String> imageUrls = imageEntities.stream()
                .map(image -> resolver == null ? image.getImageUrl() : resolver.thumbnailUrl(image))
                .toList();
        List<String> originalImageUrls = imageEntities.stream()
                .map(image -> resolver == null ? image.getImageUrl() : resolver.originalUrl(image))
                .toList();
        List<PlaceImageResponse> images = imageEntities.stream()
                .map(image -> resolver == null ? PlaceImageResponse.from(image) : PlaceImageResponse.from(image, resolver))
                .toList();
        return new PlaceDetailResponse(place.getId().toString(), place.getName(), place.getRegion(),
                place.getCategory(), place.getDescription(), imageUrls, originalImageUrls,
                place.getLatitude(), place.getLongitude(), images);
    }
}
