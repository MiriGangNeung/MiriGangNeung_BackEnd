package com.mirigangneung.place.dto;

import java.util.List;

public record AwardPhotoPageResponse(
        List<AwardPhotoResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    public AwardPhotoPageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
