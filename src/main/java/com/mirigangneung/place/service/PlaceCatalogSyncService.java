package com.mirigangneung.place.service;

import com.mirigangneung.infrastructure.image.ImageAssetCacheService;
import com.mirigangneung.infrastructure.image.PlaceImageStorage;
import com.mirigangneung.infrastructure.image.PlaceImageUrlResolver;
import com.mirigangneung.infrastructure.tourapi.TourApiClient;
import com.mirigangneung.common.redis.RedisCache;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.place.repository.PlaceImageRepository;
import com.mirigangneung.place.repository.PlaceRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlaceCatalogSyncService {
    private static final int DEFAULT_PAGE_SIZE = 1_000;
    private static final int MAX_PAGES = 100;
    private static final int MAX_IMAGES_PER_PLACE = 5;
    private static final String ALLOWED_COPYRIGHT_CODE = "Type1";
    private static final Set<String> BACKGROUND_CATEGORIES = Set.of("nature", "culture", "active");

    private final TourApiClient tourApiClient;
    private final PlaceRepository placeRepository;
    private final PlaceImageRepository placeImageRepository;
    private final TourismPhotoMatcher tourismPhotoMatcher;
    private final PlaceCatalogCleanupService cleanupService;
    private final ImageUrlValidator imageUrlValidator;
    private final RedisCache cache;
    private final ImageAssetCacheService imageAssetCacheService;
    private final PlaceImageUrlResolver imageUrlResolver;
    private final int pageSize;

    @Autowired
    public PlaceCatalogSyncService(
            TourApiClient tourApiClient,
            PlaceRepository placeRepository,
            PlaceImageRepository placeImageRepository,
            TourismPhotoMatcher tourismPhotoMatcher,
            PlaceCatalogCleanupService cleanupService,
            ImageUrlValidator imageUrlValidator,
            RedisCache cache,
            ImageAssetCacheService imageAssetCacheService,
            PlaceImageUrlResolver imageUrlResolver) {
        this(tourApiClient, placeRepository, placeImageRepository, tourismPhotoMatcher,
                cleanupService, imageUrlValidator, cache, imageAssetCacheService, imageUrlResolver,
                DEFAULT_PAGE_SIZE);
    }

    PlaceCatalogSyncService(
            TourApiClient tourApiClient,
            PlaceRepository placeRepository,
            PlaceImageRepository placeImageRepository,
            TourismPhotoMatcher tourismPhotoMatcher,
            PlaceCatalogCleanupService cleanupService,
            ImageUrlValidator imageUrlValidator,
            int pageSize) {
        this(tourApiClient, placeRepository, placeImageRepository, tourismPhotoMatcher,
                cleanupService, imageUrlValidator, null, null, legacyImageUrlResolver(), pageSize);
    }

    PlaceCatalogSyncService(
            TourApiClient tourApiClient,
            PlaceRepository placeRepository,
            PlaceImageRepository placeImageRepository,
            TourismPhotoMatcher tourismPhotoMatcher,
            PlaceCatalogCleanupService cleanupService,
            ImageUrlValidator imageUrlValidator,
            RedisCache cache,
            int pageSize) {
        this(tourApiClient, placeRepository, placeImageRepository, tourismPhotoMatcher,
                cleanupService, imageUrlValidator, cache, null, legacyImageUrlResolver(), pageSize);
    }

    PlaceCatalogSyncService(
            TourApiClient tourApiClient,
            PlaceRepository placeRepository,
            PlaceImageRepository placeImageRepository,
            TourismPhotoMatcher tourismPhotoMatcher,
            PlaceCatalogCleanupService cleanupService,
            ImageUrlValidator imageUrlValidator,
            RedisCache cache,
            ImageAssetCacheService imageAssetCacheService,
            PlaceImageUrlResolver imageUrlResolver,
            int pageSize) {
        this.tourApiClient = tourApiClient;
        this.placeRepository = placeRepository;
        this.placeImageRepository = placeImageRepository;
        this.tourismPhotoMatcher = tourismPhotoMatcher;
        this.cleanupService = cleanupService;
        this.imageUrlValidator = imageUrlValidator;
        this.cache = cache;
        this.imageAssetCacheService = imageAssetCacheService;
        this.imageUrlResolver = imageUrlResolver;
        this.pageSize = pageSize;
    }

    public SyncResult synchronizeAll() {
        List<TourApiClient.TourPlace> catalog = loadAllSummaries();
        int deletedFoodPlaces = cleanupService.deleteFoodPlaces();
        int deletedGalleryOnlyCards = cleanupService.deleteGalleryOnlyPlaces();
        List<TourApiClient.TourPlace> backgroundCatalog = catalog.stream()
                .filter(PlaceCatalogSyncService::isBackgroundPlace)
                .toList();
        int excludedByCategory = catalog.size() - backgroundCatalog.size();
        List<SyncedPlace> synced = backgroundCatalog.stream()
                .map(this::upsertPlace)
                .filter(Objects::nonNull)
                .toList();
        List<Place> savedPlaces = synced.stream().map(SyncedPlace::place).toList();
        Map<UUID, List<String>> galleryImages = tourismPhotoMatcher.findImageUrls(savedPlaces);

        int placesWithImages = 0;
        int rejectedImageUrls = 0;
        for (SyncedPlace syncedPlace : synced) {
            ImageReplacementResult replacement = replaceImages(
                    syncedPlace,
                    galleryImages.getOrDefault(syncedPlace.place().getId(), List.of()));
            rejectedImageUrls += replacement.rejectedImageUrls();
            if (replacement.hasImages()) {
                placesWithImages++;
            }
        }
        invalidatePlaceCaches();
        return new SyncResult(
                catalog.size(),
                excludedByCategory,
                deletedFoodPlaces,
                deletedGalleryOnlyCards,
                synced.size(),
                placesWithImages,
                rejectedImageUrls);
    }

    private void invalidatePlaceCaches() {
        if (cache == null) {
            return;
        }
        cache.deleteByPrefix("place:list:");
        cache.deleteByPrefix("place:detail:");
    }

    private List<TourApiClient.TourPlace> loadAllSummaries() {
        List<TourApiClient.TourPlace> result = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            List<TourApiClient.TourPlace> batch = tourApiClient.searchSummaries(null, null, page, pageSize);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            result.addAll(batch);
            if (batch.size() < pageSize) {
                break;
            }
        }
        return result;
    }

    private SyncedPlace upsertPlace(TourApiClient.TourPlace tourPlace) {
        if (tourPlace == null || !hasText(tourPlace.contentId()) || !hasText(tourPlace.name())) {
            return null;
        }
        List<TourApiClient.TourImage> summaryImages = allowedImages(tourPlace.images());
        String thumbnailUrl = summaryImages.stream()
                .map(TourApiClient.TourImage::imageUrl)
                .findFirst()
                .orElse(null);
        Place incoming = new Place(
                tourPlace.contentId(),
                tourPlace.name(),
                tourPlace.region(),
                tourPlace.category(),
                tourPlace.description(),
                tourPlace.latitude(),
                tourPlace.longitude(),
                thumbnailUrl,
                "KTO",
                tourPlace.sourceUpdatedAt());
        Place place = placeRepository.findByTourContentId(tourPlace.contentId()).orElse(null);
        if (place == null) {
            place = incoming;
        } else {
            place.updateFrom(incoming, tourPlace.sourceUpdatedAt());
        }
        return new SyncedPlace(placeRepository.save(place), summaryImages);
    }

    private ImageReplacementResult replaceImages(SyncedPlace syncedPlace, List<String> galleryUrls) {
        Place place = syncedPlace.place();
        Map<String, ImageData> byUrl = new LinkedHashMap<>();
        placeImageRepository.findByPlaceOrderBySortOrderAsc(place).stream()
                .filter(PlaceCatalogSyncService::isAllowedImage)
                .forEach(image -> addImage(byUrl, new ImageData(
                        image.getImageUrl(), image.getTitle(), image.getSource(), image.getCopyrightCode())));
        syncedPlace.summaryImages().forEach(image -> addImage(byUrl, new ImageData(
                image.imageUrl(), image.title(), "KTO", image.copyrightCode())));
        galleryUrls.forEach(url -> addImage(byUrl, new ImageData(
                url, place.getName(), "KTO_PHOTO_GALLERY", ALLOWED_COPYRIGHT_CODE)));

        List<CachedImage> usableImages;
        if (cachingEnabled()) {
            usableImages = byUrl.values().stream()
                    .map(this::cacheImage)
                    .flatMap(java.util.Optional::stream)
                    .toList();
        } else {
            usableImages = byUrl.values().stream()
                    .filter(image -> imageUrlValidator.isUsable(image.url()))
                    .map(image -> new CachedImage(image, null))
                    .toList();
        }
        int rejectedImageUrls = byUrl.size() - usableImages.size();

        placeImageRepository.deleteByPlace(place);
        int sortOrder = 0;
        PlaceImage firstSavedImage = null;
        for (CachedImage cachedImage : usableImages.stream().limit(MAX_IMAGES_PER_PLACE).toList()) {
            ImageData image = cachedImage.source();
            PlaceImageStorage.StoredImage stored = cachedImage.stored();
            PlaceImage placeImage = stored == null
                    ? new PlaceImage(place, image.url(), image.title(), image.source(), sortOrder++, image.copyrightCode())
                    : new PlaceImage(
                    place,
                    image.url(),
                    image.title(),
                    image.source(),
                    sortOrder++,
                    image.copyrightCode(),
                    stored.originalStorageKey(),
                    stored.thumbnailStorageKey(),
                    stored.contentType(),
                    stored.originalByteSize(),
                    stored.thumbnailByteSize());
            placeImageRepository.save(placeImage);
            if (firstSavedImage == null) {
                firstSavedImage = placeImage;
            }
        }
        if (cachingEnabled()) {
            place.updateThumbnailUrl(firstSavedImage == null
                    ? null
                    : imageUrlResolver.thumbnailUrl(firstSavedImage));
            placeRepository.save(place);
        }
        return new ImageReplacementResult(sortOrder > 0, rejectedImageUrls);
    }

    private java.util.Optional<CachedImage> cacheImage(ImageData image) {
        return imageAssetCacheService.ensureCached(image.url())
                .map(stored -> new CachedImage(image, stored));
    }

    private boolean cachingEnabled() {
        return imageAssetCacheService != null && imageAssetCacheService.enabled();
    }

    private static PlaceImageUrlResolver legacyImageUrlResolver() {
        return new PlaceImageUrlResolver(new com.mirigangneung.infrastructure.image.ImageCacheProperties(
                false,
                System.getProperty("java.io.tmpdir") + "/mirigangneung-images",
                "http://localhost:8080/media/images",
                java.time.Duration.ofSeconds(10),
                10 * 1024 * 1024,
                640,
                java.time.Duration.ofDays(365)));
    }

    private static List<TourApiClient.TourImage> allowedImages(List<TourApiClient.TourImage> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream()
                .filter(Objects::nonNull)
                .filter(image -> hasText(image.imageUrl()))
                .filter(image -> isAllowedCopyright(image.copyrightCode()))
                .sorted(Comparator.comparingInt(TourApiClient.TourImage::sortOrder))
                .limit(MAX_IMAGES_PER_PLACE)
                .toList();
    }

    private static boolean isAllowedImage(PlaceImage image) {
        return image != null
                && hasText(image.getImageUrl())
                && isAllowedCopyright(image.getCopyrightCode());
    }

    private static boolean isAllowedCopyright(String code) {
        return ALLOWED_COPYRIGHT_CODE.equalsIgnoreCase(code == null ? "" : code.trim());
    }

    private static void addImage(Map<String, ImageData> byUrl, ImageData image) {
        if (image != null && hasText(image.url())) {
            byUrl.putIfAbsent(image.url().trim(), image);
        }
    }

    private static boolean isBackgroundPlace(TourApiClient.TourPlace place) {
        return place != null && BACKGROUND_CATEGORIES.contains(place.category());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record SyncResult(
            int fetchedPlaces,
            int excludedByCategory,
            int deletedFoodPlaces,
            int deletedGalleryOnlyCards,
            int savedPlaces,
            int placesWithImages,
            int rejectedImageUrls) {
    }

    private record SyncedPlace(Place place, List<TourApiClient.TourImage> summaryImages) {
    }

    private record ImageData(String url, String title, String source, String copyrightCode) {
    }

    private record CachedImage(ImageData source, PlaceImageStorage.StoredImage stored) {
    }

    private record ImageReplacementResult(boolean hasImages, int rejectedImageUrls) {
    }
}
