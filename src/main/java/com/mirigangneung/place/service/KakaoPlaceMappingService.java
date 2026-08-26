package com.mirigangneung.place.service;

import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.repository.PlaceRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KakaoPlaceMappingService {
    private final PlaceRepository placeRepository;
    private final KakaoPlaceMappingCatalog mappingCatalog;

    public KakaoPlaceMappingService(
            PlaceRepository placeRepository,
            KakaoPlaceMappingCatalog mappingCatalog) {
        this.placeRepository = placeRepository;
        this.mappingCatalog = mappingCatalog;
    }

    @Transactional
    public MappingResult applyMappings() {
        List<KakaoPlaceMapping> mappings = mappingCatalog.mappings();
        if (mappings.isEmpty()) {
            return MappingResult.empty();
        }

        List<String> contentIds = mappings.stream()
                .map(KakaoPlaceMapping::tourContentId)
                .toList();
        Map<String, Place> placesByContentId = placeRepository
                .findAllByTourContentIdIn(contentIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Place::getTourContentId,
                        place -> place,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        List<Place> changedPlaces = new ArrayList<>();
        int suppressedPlaces = 0;
        int missingPlaces = 0;
        for (KakaoPlaceMapping mapping : mappings) {
            Place place = placesByContentId.get(mapping.tourContentId());
            if (place == null) {
                missingPlaces++;
                continue;
            }
            if (Objects.equals(place.getKakaoPlaceId(), mapping.kakaoPlaceId())
                    && Objects.equals(place.getKakaoPlaceUrl(), mapping.kakaoPlaceUrl())) {
                continue;
            }
            place.linkKakaoPlace(mapping.kakaoPlaceId(), mapping.kakaoPlaceUrl());
            changedPlaces.add(place);
            if (mapping.suppressed()) {
                suppressedPlaces++;
            }
        }

        if (!changedPlaces.isEmpty()) {
            placeRepository.saveAll(changedPlaces);
        }
        return new MappingResult(
                mappings.size(),
                changedPlaces.size(),
                suppressedPlaces,
                missingPlaces);
    }

    public record MappingResult(
            int configuredMappings,
            int updatedPlaces,
            int suppressedPlaces,
            int missingPlaces) {
        static MappingResult empty() {
            return new MappingResult(0, 0, 0, 0);
        }
    }
}
