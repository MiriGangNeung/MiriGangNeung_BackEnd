package com.mirigangneung.place.service;

import com.mirigangneung.infrastructure.tourapi.AwardPhotoApiClient;
import com.mirigangneung.place.dto.AwardPhotoPageResponse;
import com.mirigangneung.place.dto.AwardPhotoResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class AwardPhotoService {
    private static final String SOURCE = "KTO_AWARD";
    private static final String GANGNEUNG = "강릉";

    private final AwardPhotoApiClient client;

    public AwardPhotoService(AwardPhotoApiClient client) {
        this.client = client;
    }

    public AwardPhotoPageResponse search(String region, int page, int size) {
        List<AwardPhotoResponse> content = client.search(region, page, size).stream()
                .filter(Objects::nonNull)
                .map(this::toResponse)
                .filter(photo -> hasText(photo.location()) && photo.location().contains(GANGNEUNG))
                .filter(photo -> hasText(photo.originalImageUrl()) || hasText(photo.thumbnailUrl()))
                .toList();
        return new AwardPhotoPageResponse(
                content,
                page,
                size,
                content.size(),
                content.isEmpty() ? 0 : 1);
    }

    private AwardPhotoResponse toResponse(AwardPhotoApiClient.AwardPhoto photo) {
        return new AwardPhotoResponse(
                photo.contentId(),
                photo.title(),
                photo.location(),
                photo.award(),
                photo.keywords(),
                photo.originalImageUrl(),
                photo.thumbnailUrl(),
                photo.photographer(),
                photo.copyrightCode(),
                SOURCE);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
