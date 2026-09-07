package com.mirigangneung.composition.service;

import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.infrastructure.ai.AiGenerationClient.ImagePayload;
import com.mirigangneung.infrastructure.image.ImageAssetCacheService;
import com.mirigangneung.infrastructure.image.PlaceImageStorage;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.place.repository.PlaceImageRepository;
import com.mirigangneung.place.repository.PlaceRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CompositionBackgroundResolver {
    private static final String EDITABLE_COPYRIGHT_CODE = "Type1";

    private final PlaceRepository places;
    private final PlaceImageRepository images;
    private final PlaceImageStorage storage;
    private final ImageAssetCacheService cacheService;

    public CompositionBackgroundResolver(
            PlaceRepository places,
            PlaceImageRepository images,
            PlaceImageStorage storage,
            ImageAssetCacheService cacheService) {
        this.places = places;
        this.images = images;
        this.storage = storage;
        this.cacheService = cacheService;
    }

    public ResolvedBackground resolve(String onePickId, String requestedImageUrl) {
        UUID placeId;
        try {
            placeId = UUID.fromString(onePickId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(
                    "INVALID_ONE_PICK_ID",
                    HttpStatus.BAD_REQUEST,
                    "onePickId는 관광지 Place.id UUID여야 합니다.");
        }

        Place place = places.findById(placeId).orElseThrow(() -> new ApiException(
                "PLACE_NOT_FOUND", HttpStatus.NOT_FOUND, "관광지를 찾을 수 없습니다."));
        List<PlaceImage> editableImages = images.findByPlaceOrderBySortOrderAsc(place).stream()
                .filter(CompositionBackgroundResolver::isEditable)
                .toList();
        if (editableImages.isEmpty()) {
            throw new ApiException(
                    "EDITABLE_BACKGROUND_NOT_FOUND",
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "AI 합성에 사용할 수 있는 Type1 관광지 이미지가 없습니다.");
        }

        PlaceImage selected = selectImage(editableImages, requestedImageUrl);
        StoredBackground stored = findStored(selected);
        return new ResolvedBackground(
                place,
                selected.getImageUrl(),
                new ImagePayload(stored.bytes(), stored.contentType(), stored.filename()));
    }

    private PlaceImage selectImage(List<PlaceImage> candidates, String requestedImageUrl) {
        if (!hasText(requestedImageUrl)) {
            return candidates.get(0);
        }
        return candidates.stream()
                .filter(image -> requestedImageUrl.equals(image.getImageUrl())
                        || hasText(image.getOriginalStorageKey())
                        && requestedImageUrl.endsWith("/" + image.getOriginalStorageKey())
                        || hasText(image.getThumbnailStorageKey())
                        && requestedImageUrl.endsWith("/" + image.getThumbnailStorageKey()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        "BACKGROUND_IMAGE_NOT_FOUND",
                        HttpStatus.BAD_REQUEST,
                        "선택한 배경 이미지가 해당 관광지의 Type1 이미지가 아닙니다."));
    }

    private StoredBackground findStored(PlaceImage image) {
        if (hasText(image.getOriginalStorageKey())) {
            StoredBackground opened = open(image.getOriginalStorageKey());
            if (opened != null) {
                return opened;
            }
        }
        return cacheService.ensureCached(image.getImageUrl())
                .map(cached -> open(cached.originalStorageKey()))
                .orElseThrow(() -> new ApiException(
                        "BACKGROUND_IMAGE_UNAVAILABLE",
                        HttpStatus.BAD_GATEWAY,
                        "관광지 원본 이미지를 준비할 수 없습니다."));
    }

    private StoredBackground open(String storageKey) {
        try {
            var asset = storage.open(storageKey).orElse(null);
            if (asset == null) {
                return null;
            }
            try (InputStream input = asset.input()) {
                return new StoredBackground(input.readAllBytes(), asset.contentType(), storageKey);
            }
        } catch (IOException exception) {
            return null;
        }
    }

    private static boolean isEditable(PlaceImage image) {
        return image != null
                && EDITABLE_COPYRIGHT_CODE.equalsIgnoreCase(trim(image.getCopyrightCode()))
                && hasText(image.getImageUrl());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ResolvedBackground(Place place, String sourceImageUrl, ImagePayload image) {
    }

    private record StoredBackground(byte[] bytes, String contentType, String filename) {
    }
}
