package com.mirigangneung.place.service;

import com.mirigangneung.infrastructure.kakao.KakaoLocalClient;
import com.mirigangneung.infrastructure.kakao.KakaoLocalProperties;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.repository.PlaceRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links KTO places to their Kakao place detail page without replacing KTO
 * content or images.
 */
@Service
public class KakaoPlaceEnrichmentService {
    private static final Logger log = LoggerFactory.getLogger(KakaoPlaceEnrichmentService.class);
    private static final int MAX_KAKAO_PAGES = 3;
    private static final String KTO_SOURCE = "KTO";

    private final KakaoLocalClient localClient;
    private final KakaoLocalProperties properties;
    private final PlaceRepository placeRepository;

    @Autowired
    public KakaoPlaceEnrichmentService(
            KakaoLocalClient localClient,
            KakaoLocalProperties properties,
            PlaceRepository placeRepository) {
        this.localClient = localClient;
        this.properties = properties;
        this.placeRepository = placeRepository;
    }

    public KakaoPlaceEnrichmentService(
            KakaoLocalClient localClient,
            KakaoLocalProperties properties) {
        this(localClient, properties, null);
    }

    @Transactional
    public EnrichmentResult enrichMissingPlaces() {
        if (placeRepository == null) {
            return EnrichmentResult.empty();
        }
        return enrich(placeRepository.findBySource(KTO_SOURCE));
    }

    public EnrichmentResult enrich(List<Place> places) {
        if (places == null || places.isEmpty() || !hasConfiguredKey()) {
            return EnrichmentResult.empty();
        }

        int attempted = 0;
        int matched = 0;
        int skipped = 0;
        int failed = 0;
        for (Place place : places) {
            if (!canEnrich(place) || hasKakaoLink(place)) {
                skipped++;
                continue;
            }
            attempted++;
            try {
                Optional<KakaoLocalClient.NearbyPlace> match = findMatch(place);
                if (match.isEmpty()) {
                    continue;
                }
                KakaoLocalClient.NearbyPlace kakaoPlace = match.get();
                place.linkKakaoPlace(kakaoPlace.externalPlaceId(), kakaoPlace.placeUrl());
                if (placeRepository != null) {
                    placeRepository.save(place);
                }
                matched++;
            } catch (RuntimeException exception) {
                failed++;
                log.warn("Kakao place enrichment failed: name={}", place.getName());
            }
        }
        return new EnrichmentResult(attempted, matched, skipped, failed);
    }

    private Optional<KakaoLocalClient.NearbyPlace> findMatch(Place place) {
        String normalizedName = PlaceNameNormalizer.normalize(place.getName());
        int pageSize = Math.max(1, Math.min(properties.pageSize(), 15));
        for (int page = 0; page < MAX_KAKAO_PAGES; page++) {
            List<KakaoLocalClient.NearbyPlace> results = localClient.searchByKeyword(
                    place.getName(),
                    place.getLongitude(),
                    place.getLatitude(),
                    properties.radiusMeters(),
                    page,
                    pageSize);
            Optional<KakaoLocalClient.NearbyPlace> match = results == null
                    ? Optional.empty()
                    : results.stream()
                    .filter(Objects::nonNull)
                    .filter(candidate -> hasText(candidate.externalPlaceId()))
                    .filter(candidate -> hasText(candidate.placeUrl()))
                    .filter(candidate -> normalizedName.equals(PlaceNameNormalizer.normalize(candidate.name())))
                    .filter(candidate -> distanceMeters(
                            place.getLatitude(),
                            place.getLongitude(),
                            candidate.latitude(),
                            candidate.longitude()) <= properties.radiusMeters())
                    .min(Comparator.comparingInt(candidate -> distanceMeters(
                            place.getLatitude(),
                            place.getLongitude(),
                            candidate.latitude(),
                            candidate.longitude())));
            if (match.isPresent()) {
                return match;
            }
            if (results == null || results.size() < pageSize) {
                break;
            }
        }
        return Optional.empty();
    }

    private boolean canEnrich(Place place) {
        return place != null
                && hasText(place.getName())
                && place.getLatitude() != null
                && place.getLongitude() != null;
    }

    private boolean hasConfiguredKey() {
        return properties.key() != null && !properties.key().isBlank();
    }

    private static boolean hasKakaoLink(Place place) {
        return hasText(place.getKakaoPlaceId()) && hasText(place.getKakaoPlaceUrl());
    }

    private static int distanceMeters(double latitude1, double longitude1, double latitude2, double longitude2) {
        double earthRadius = 6_371_000;
        double latitudeDistance = Math.toRadians(latitude2 - latitude1);
        double longitudeDistance = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        return (int) Math.round(earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record EnrichmentResult(
            int attemptedPlaces,
            int matchedPlaces,
            int skippedPlaces,
            int failedPlaces) {
        public static EnrichmentResult empty() {
            return new EnrichmentResult(0, 0, 0, 0);
        }
    }
}
