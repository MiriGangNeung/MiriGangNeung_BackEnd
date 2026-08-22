package com.mirigangneung.place.dto;

import java.util.List;

public record AwardPhotoResponse(
        String id,
        String title,
        String location,
        String award,
        List<String> keywords,
        String originalImageUrl,
        String thumbnailUrl,
        String photographer,
        String copyrightCode,
        String source) {
    public AwardPhotoResponse {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
