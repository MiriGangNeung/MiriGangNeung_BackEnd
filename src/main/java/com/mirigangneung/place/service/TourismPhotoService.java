package com.mirigangneung.place.service;

import com.mirigangneung.infrastructure.tourapi.PhotoGalleryApiClient;
import com.mirigangneung.place.dto.TourismPhotoPageResponse;
import com.mirigangneung.place.dto.TourismPhotoResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class TourismPhotoService {
    private static final String SOURCE = "KTO_PHOTO_GALLERY";
    private static final String GANGNEUNG = "강릉";

    private final PhotoGalleryApiClient client;

    public TourismPhotoService(PhotoGalleryApiClient client) {
        this.client = client;
    }

    public TourismPhotoPageResponse search(int page, int size) {
        List<TourismPhotoResponse> content = client.search(GANGNEUNG, page, size).stream()
                .filter(Objects::nonNull)
                .map(this::toResponse)
                .filter(photo -> hasText(photo.location()) && photo.location().contains(GANGNEUNG))
                .filter(photo -> hasText(photo.originalImageUrl()) || hasText(photo.thumbnailUrl()))
                .toList();
        return new TourismPhotoPageResponse(
                content,
                page,
                size,
                content.size(),
                content.isEmpty() ? 0 : 1);
    }

    private TourismPhotoResponse toResponse(PhotoGalleryApiClient.PhotoGalleryPhoto photo) {
        return new TourismPhotoResponse(
                photo.contentId(),
                photo.title(),
                photo.location(),
                photo.photographyMonth(),
                photo.keywords(),
                photo.originalImageUrl(),
                photo.thumbnailUrl(),
                photo.photographer(),
                SOURCE);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
