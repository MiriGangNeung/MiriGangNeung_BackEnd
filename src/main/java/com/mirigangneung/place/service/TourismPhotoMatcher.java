package com.mirigangneung.place.service;

import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.infrastructure.tourapi.PhotoGalleryApiClient;
import com.mirigangneung.place.domain.Place;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

        Map<String, GalleryPhotoGroup> groups;
        try {
            groups = catalog();
        } catch (ApiException exception) {
            log.warn("Using stored place images because tourism photo gallery loading failed");
            return Map.of();
        }

        Map<UUID, List<String>> result = new LinkedHashMap<>();
        for (Place place : places) {
            if (place == null || place.getId() == null) {
                continue;
            }
            GalleryPhotoGroup group = groups.get(PlaceNameNormalizer.normalize(place.getName()));
            if (group != null && !group.imageUrls().isEmpty()) {
                result.put(place.getId(), group.imageUrls());
            }
        }
        return result;
    }

    private Map<String, GalleryPhotoGroup> catalog() {
        CachedCatalog current = cachedCatalog;
        Instant now = clock.instant();
        if (current != null && current.expiresAt().isAfter(now)) {
            return current.groups();
        }

        synchronized (this) {
            current = cachedCatalog;
            now = clock.instant();
            if (current != null && current.expiresAt().isAfter(now)) {
                return current.groups();
            }
            Map<String, GalleryPhotoGroup> loaded = loadCatalog();
            cachedCatalog = new CachedCatalog(loaded, now.plus(cacheTtl));
            return loaded;
        }
    }

    private Map<String, GalleryPhotoGroup> loadCatalog() {
        Map<String, GalleryPhotoGroupBuilder> collected = new LinkedHashMap<>();
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

        Map<String, GalleryPhotoGroup> result = new LinkedHashMap<>();
        collected.forEach((title, group) -> result.put(title, group.build()));
        return Map.copyOf(result);
    }

    private static void collect(
            Map<String, GalleryPhotoGroupBuilder> collected,
            PhotoGalleryApiClient.PhotoGalleryPhoto photo) {
        String normalizedTitle = PlaceNameNormalizer.normalize(photo.title());
        String imageUrl = firstText(photo.originalImageUrl(), photo.thumbnailUrl());
        if (normalizedTitle.isBlank() || imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        GalleryPhotoGroupBuilder group = collected.computeIfAbsent(
                normalizedTitle,
                ignored -> new GalleryPhotoGroupBuilder());
        group.addImage(imageUrl.trim());
    }

    private static String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CachedCatalog(Map<String, GalleryPhotoGroup> groups, Instant expiresAt) {
    }

    private record GalleryPhotoGroup(List<String> imageUrls) {
    }

    private static final class GalleryPhotoGroupBuilder {
        private final LinkedHashSet<String> imageUrls = new LinkedHashSet<>();

        private void addImage(String imageUrl) {
            if (imageUrls.size() < MAX_IMAGES_PER_PLACE) {
                imageUrls.add(imageUrl);
            }
        }

        private GalleryPhotoGroup build() {
            return new GalleryPhotoGroup(List.copyOf(imageUrls));
        }
    }
}
