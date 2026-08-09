package com.mirigangneung.place.dto;

import java.util.List;

public record TourismPhotoResponse(
        String id,
        String title,
        String location,
        String photographyMonth,
        List<String> keywords,
        String originalImageUrl,
        String thumbnailUrl,
        String photographer,
        String source) {
    public TourismPhotoResponse {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
