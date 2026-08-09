package com.mirigangneung.place.dto;

import com.mirigangneung.place.domain.PlaceImage;

public record PlaceImageResponse(String imageUrl, String title, String source,
                                 Integer sortOrder, String copyrightCode) {
    public static PlaceImageResponse from(PlaceImage image) {
        return new PlaceImageResponse(image.getImageUrl(), image.getTitle(), image.getSource(),
                image.getSortOrder(), image.getCopyrightCode());
    }
}
