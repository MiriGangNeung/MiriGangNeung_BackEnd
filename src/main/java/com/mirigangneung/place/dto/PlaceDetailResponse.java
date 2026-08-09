package com.mirigangneung.place.dto;

import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;

import java.util.List;

public record PlaceDetailResponse(String id, String name, String region, String category, String description,
                                  List<String> imageUrls, Double latitude, Double longitude,
                                  List<PlaceImageResponse> images) {
    public PlaceDetailResponse(String id, String name, String region, String category, String description,
                               List<String> imageUrls, Double latitude, Double longitude) {
        this(id, name, region, category, description, imageUrls, latitude, longitude,
                imageUrls == null ? List.of() : imageUrls.stream()
                        .map(url -> new PlaceImageResponse(url, null, null, null, null))
                        .toList());
    }

    public static PlaceDetailResponse from(Place place, List<String> imageUrls) {
        return new PlaceDetailResponse(place.getId().toString(), place.getName(), place.getRegion(),
                place.getCategory(), place.getDescription(), imageUrls, place.getLatitude(), place.getLongitude(),
                imageUrls.stream().map(url -> new PlaceImageResponse(url, null, null, null, null)).toList());
    }

    public static PlaceDetailResponse fromImages(Place place, List<PlaceImage> imageEntities) {
        List<String> imageUrls = imageEntities.stream().map(PlaceImage::getImageUrl).toList();
        List<PlaceImageResponse> images = imageEntities.stream().map(PlaceImageResponse::from).toList();
        return new PlaceDetailResponse(place.getId().toString(), place.getName(), place.getRegion(),
                place.getCategory(), place.getDescription(), imageUrls, place.getLatitude(), place.getLongitude(), images);
    }
}
