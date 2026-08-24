package com.mirigangneung.place.service;

import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.infrastructure.tourapi.PhotoGalleryApiClient;
import com.mirigangneung.place.domain.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TourismPhotoMatcher {
    private static final Logger log = LoggerFactory.getLogger(TourismPhotoMatcher.class);
    private static final String GANGNEUNG = "강릉";
    private static final int PAGE_SIZE = 2_000;
    private static final int MAX_PAGES = 50;
    private static final int MAX_IMAGES_PER_PLACE = 5;

    private final PhotoGalleryApiClient client;
    private final Clock clock;
    private final Duration cacheTtl;
    private volatile CachedCatalog cachedCatalog;

    @Autowired
    public TourismPhotoMatcher(PhotoGalleryApiClient client) {
        this(client, Clock.systemUTC(), Duration.ofHours(1));
    }

    TourismPhotoMatcher(PhotoGalleryApiClient client, Clock clock, Duration cacheTtl) {
        this.client = client;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
    }

    public Map<UUID, List<String>> findImageUrls(List<Place> places) {
        if (places == null || places.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> imagesByNormalizedTitle;
        try {
            imagesByNormalizedTitle = catalog();
        } catch (ApiException exception) {
            log.warn("Using stored place images because tourism photo gallery loading failed");
            return Map.of();
        }

        Map<UUID, List<String>> result = new LinkedHashMap<>();
        for (Place place : places) {
            if (place == null || place.getId() == null) {
                continue;
            }
            List<String> matched = imagesByNormalizedTitle.get(PlaceNameNormalizer.normalize(place.getName()));
            if (matched != null && !matched.isEmpty()) {
                result.put(place.getId(), matched);
            }
        }
        return result;
    }

    private Map<String, List<String>> catalog() {
        CachedCatalog current = cachedCatalog;
        Instant now = clock.instant();
        if (current != null && current.expiresAt().isAfter(now)) {
            return current.imagesByNormalizedTitle();
        }

        synchronized (this) {
            current = cachedCatalog;
            now = clock.instant();
            if (current != null && current.expiresAt().isAfter(now)) {
                return current.imagesByNormalizedTitle();
            }
            Map<String, List<String>> loaded = loadCatalog();
            cachedCatalog = new CachedCatalog(loaded, now.plus(cacheTtl));
            return loaded;
        }
    }

    private Map<String, List<String>> loadCatalog() {
        Map<String, LinkedHashSet<String>> collected = new LinkedHashMap<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            List<PhotoGalleryApiClient.PhotoGalleryPhoto> batch = client.search(GANGNEUNG, page, PAGE_SIZE);
            if (batch == null) {
                batch = List.of();
            }
            batch.stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(photo -> hasText(photo.location()) && photo.location().contains(GANGNEUNG))
                    .forEach(photo -> collect(collected, photo));
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        collected.forEach((title, urls) -> result.put(title, List.copyOf(urls)));
        return Map.copyOf(result);
    }

    private static void collect(Map<String, LinkedHashSet<String>> collected,
                                PhotoGalleryApiClient.PhotoGalleryPhoto photo) {
        String normalizedTitle = PlaceNameNormalizer.normalize(photo.title());
        String imageUrl = firstText(photo.originalImageUrl(), photo.thumbnailUrl());
        if (!hasText(normalizedTitle) || !hasText(imageUrl)) {
            return;
        }

        LinkedHashSet<String> urls = collected.computeIfAbsent(normalizedTitle, ignored -> new LinkedHashSet<>());
        if (urls.size() < MAX_IMAGES_PER_PLACE) {
            urls.add(imageUrl.trim());
        }
    }

    private static String firstText(String first, String second) {
        if (hasText(first)) {
            return first;
        }
        return hasText(second) ? second : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CachedCatalog(Map<String, List<String>> imagesByNormalizedTitle, Instant expiresAt) {
    }
}
