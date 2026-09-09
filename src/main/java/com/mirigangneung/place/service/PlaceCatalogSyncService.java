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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlaceCatalogSyncService {
    private static final Logger log = LoggerFactory.getLogger(PlaceCatalogSyncService.class);
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
        List<TourApiClient.TourPlace> categoryCatalog = catalog.stream()
                .filter(PlaceCatalogSyncService::isBackgroundPlace)
                .toList();
        int excludedByCategory = catalog.size() - categoryCatalog.size();
        List<TourApiClient.TourPlace> backgroundCatalog = categoryCatalog.stream()
                .filter(PromptPlaceCatalog::contains)
                .toList();
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

    /**
     * AI 에이전트가 "인물이 설 자리가 있고 배경으로 쓸 만하다"고 판정한 사진만 남긴다
     * (노션 '배경 사진 VLM 사전 분석 리포트'의 노출 규칙 1).
     *
     * <p>한 장도 일치하지 않으면 아무것도 지우지 않고 경고만 남긴다. 판정 데이터는
     * 특정 시점의 KorService2 이미지 URL로 찍혀 있어서, 관광공사가 사진을 교체하면
     * 전부 어긋난다. 그때 전량을 지우면 장소가 이미지 0장이 되어 통째로 사라지는데,
     * 이는 "부적합 사진을 감춘다"보다 훨씬 나쁜 결과다.
     */
    private void retainPortraitViableImages(Place place, Map<String, ImageData> byUrl) {
        Set<String> usable = PromptPlaceCatalog.usableImageUrls(place.getName());
        if (usable.isEmpty() || byUrl.isEmpty()) {
            return;
        }
        List<String> excluded = byUrl.keySet().stream()
                .filter(url -> !usable.contains(url))
                .toList();
        if (excluded.size() == byUrl.size()) {
            log.warn("판정된 배경 사진과 일치하는 URL이 없어 사진 필터를 건너뜁니다: place={}, 후보={}",
                    place.getName(), byUrl.size());
            return;
        }
        excluded.forEach(byUrl::remove);
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
        retainPortraitViableImages(place, byUrl);

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
        if (cachingEnabled() || (!hasText(place.getThumbnailUrl()) && firstSavedImage != null)) {
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
