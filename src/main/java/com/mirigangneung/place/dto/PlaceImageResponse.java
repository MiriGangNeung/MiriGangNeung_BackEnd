package com.mirigangneung.place.dto;

import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.infrastructure.image.PlaceImageUrlResolver;

public record PlaceImageResponse(String imageUrl, String originalImageUrl, String sourceImageUrl,
                                 String title, String source, Integer sortOrder, String copyrightCode) {
    public PlaceImageResponse(String imageUrl, String title, String source,
                               Integer sortOrder, String copyrightCode) {
        this(imageUrl, imageUrl, imageUrl, title, source, sortOrder, copyrightCode);
    }

    public static PlaceImageResponse from(PlaceImage image) {
        return new PlaceImageResponse(image.getImageUrl(), image.getImageUrl(), image.getImageUrl(),
                image.getTitle(), image.getSource(), image.getSortOrder(), image.getCopyrightCode());
    }

    public static PlaceImageResponse from(PlaceImage image, PlaceImageUrlResolver resolver) {
        return new PlaceImageResponse(
                resolver.thumbnailUrl(image),
                resolver.originalUrl(image),
                resolver.sourceUrl(image),
                image.getTitle(),
                image.getSource(),
                image.getSortOrder(),
                image.getCopyrightCode());
    }
}
