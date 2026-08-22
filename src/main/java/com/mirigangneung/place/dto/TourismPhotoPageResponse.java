package com.mirigangneung.place.dto;

import java.util.List;

public record TourismPhotoPageResponse(
        List<TourismPhotoResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    public TourismPhotoPageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
