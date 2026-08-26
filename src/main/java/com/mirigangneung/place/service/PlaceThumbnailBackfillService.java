package com.mirigangneung.place.service;

import com.mirigangneung.infrastructure.image.PlaceImageUrlResolver;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.place.repository.PlaceImageRepository;
import com.mirigangneung.place.repository.PlaceRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceThumbnailBackfillService {
    private static final String ALLOWED_COPYRIGHT_CODE = "Type1";

    private final PlaceRepository placeRepository;
    private final PlaceImageRepository placeImageRepository;
    private final PlaceImageUrlResolver imageUrlResolver;

    public PlaceThumbnailBackfillService(
            PlaceRepository placeRepository,
            PlaceImageRepository placeImageRepository,
            PlaceImageUrlResolver imageUrlResolver) {
        this.placeRepository = placeRepository;
        this.placeImageRepository = placeImageRepository;
        this.imageUrlResolver = imageUrlResolver;
    }

    @Transactional
    public int backfillMissingThumbnails() {
        List<Place> placesWithoutThumbnail = placeRepository.findAll().stream()
                .filter(place -> !hasText(place.getThumbnailUrl()))
                .toList();
        if (placesWithoutThumbnail.isEmpty()) {
            return 0;
        }

        Map<Place, String> firstImageByPlace = new HashMap<>();
        placeImageRepository.findByPlaceInOrderBySortOrderAsc(placesWithoutThumbnail).stream()
                .filter(PlaceThumbnailBackfillService::isAllowedImage)
                .forEach(image -> firstImageByPlace.putIfAbsent(
                        image.getPlace(), imageUrlResolver.thumbnailUrl(image)));

        List<Place> updatedPlaces = placesWithoutThumbnail.stream()
                .filter(place -> hasText(firstImageByPlace.get(place)))
                .peek(place -> place.updateThumbnailUrl(firstImageByPlace.get(place)))
                .toList();
        if (!updatedPlaces.isEmpty()) {
            placeRepository.saveAll(updatedPlaces);
        }
        return updatedPlaces.size();
    }

    private static boolean isAllowedImage(PlaceImage image) {
        return image != null
                && image.getPlace() != null
                && hasText(image.getImageUrl())
                && ALLOWED_COPYRIGHT_CODE.equalsIgnoreCase(
                image.getCopyrightCode() == null ? "" : image.getCopyrightCode().trim());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
