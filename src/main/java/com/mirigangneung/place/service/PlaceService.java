package com.mirigangneung.place.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.common.redis.RedisCache;
import com.mirigangneung.infrastructure.tourapi.TourApiCacheProperties;
import com.mirigangneung.infrastructure.tourapi.TourApiClient;
import com.mirigangneung.infrastructure.tourapi.TourCategoryMapper;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.place.dto.PlaceDetailResponse;
import com.mirigangneung.place.dto.PlacePageResponse;
import com.mirigangneung.place.dto.PlaceResponse;
import com.mirigangneung.place.repository.PlaceImageRepository;
import com.mirigangneung.place.repository.PlaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class PlaceService {
    private static final Logger log = LoggerFactory.getLogger(PlaceService.class);
    private static final int MAX_PLACE_IMAGES = 5;
    private static final String ALLOWED_COPYRIGHT_CODE = "Type1";

    private final PlaceRepository places;
    private final PlaceImageRepository images;
    private final TourApiClient tour;
    private final RedisCache cache;
    private final ObjectMapper objectMapper;
    private final TourApiCacheProperties cacheProperties;

    @Autowired
    public PlaceService(PlaceRepository places, PlaceImageRepository images, TourApiClient tour,
                        RedisCache cache, ObjectMapper objectMapper, TourApiCacheProperties cacheProperties) {
        this.places = places;
        this.images = images;
        this.tour = tour;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    public PlaceService(PlaceRepository places, PlaceImageRepository images, TourApiClient tour) {
        this(places, images, tour, null, new ObjectMapper(),
                new TourApiCacheProperties(java.time.Duration.ofMinutes(5), java.time.Duration.ofHours(1)));
    }

    @Transactional
    public PlacePageResponse search(String category, String keyword, int page, int size) {
        String normalizedKeyword = keyword == null ? "" : keyword;
        String cacheKey = listCacheKey(category, normalizedKeyword, page, size);
        PlacePageResponse cached = readCached(cacheKey, PlacePageResponse.class);
        if (cached != null) {
            return cached;
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Place> result;
        if (category != null && !category.isBlank()) {
            String normalizedCategory = TourCategoryMapper.toInternalCategory(
                    TourCategoryMapper.toContentTypeId(category));
            result = places.findByCategoryContainingAndNameContaining(normalizedCategory, normalizedKeyword, pageable);
        } else {
            result = places.findByRegionContainingAndNameContaining("강릉", normalizedKeyword, pageable);
        }
        List<Place> pagePlaces = result.getContent();
        Map<UUID, List<String>> imageUrlsByPlace = imageUrlsByPlace(pagePlaces);
        PlacePageResponse response = new PlacePageResponse(
                pagePlaces.stream()
                        .map(place -> PlaceResponse.from(place, imageUrlsByPlace.get(place.getId())))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
        writeCache(cacheKey, response, cacheProperties.listTtl());
        return response;
    }

    @Transactional
    public PlaceDetailResponse detail(String id) {
        String cacheKey = detailCacheKey(id);
        PlaceDetailResponse cached = readCached(cacheKey, PlaceDetailResponse.class);
        if (cached != null) {
            return cached;
        }

        Place place = find(id);
        List<PlaceImage> allowedImages = images.findByPlaceOrderBySortOrderAsc(place).stream()
                .filter(PlaceService::isAllowedImage)
                .toList();
        PlaceDetailResponse response = PlaceDetailResponse.fromImages(place, allowedImages);
        writeCache(cacheKey, response, cacheProperties.detailTtl());
        return response;
    }

    @Transactional
    public List<PlaceResponse> candidates(String id, boolean related) {
        Place place = find(id);
        List<TourApiClient.TourPlace> results = related
                ? tour.related(place.getTourContentId())
                : (place.getLatitude() == null || place.getLongitude() == null
                ? List.of()
                : tour.nearby(place.getTourContentId(), place.getLatitude(), place.getLongitude()));
        return results.stream().map(this::upsert).filter(Objects::nonNull).map(PlaceResponse::from).toList();
    }

    @Transactional
    public Place find(String id) {
        try {
            return places.findById(UUID.fromString(id)).orElseThrow(this::notFound);
        } catch (IllegalArgumentException e) {
            return places.findByTourContentId(id).orElseThrow(this::notFound);
        }
    }

    private ApiException notFound() {
        return new ApiException("PLACE_NOT_FOUND", HttpStatus.NOT_FOUND, "관광지를 찾을 수 없습니다.");
    }

    private Map<UUID, List<String>> imageUrlsByPlace(List<Place> pagePlaces) {
        if (pagePlaces.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<String>> result = new HashMap<>();
        for (PlaceImage image : images.findByPlaceInOrderBySortOrderAsc(pagePlaces)) {
            if (image == null || image.getPlace() == null || image.getPlace().getId() == null
                    || !hasText(image.getImageUrl()) || !isAllowedImage(image)) {
                continue;
            }
            List<String> urls = result.computeIfAbsent(image.getPlace().getId(), ignored -> new java.util.ArrayList<>());
            String imageUrl = image.getImageUrl().trim();
            if (urls.size() < MAX_PLACE_IMAGES && !urls.contains(imageUrl)) {
                urls.add(imageUrl);
            }
        }
        return result;
    }

    private Place upsert(TourApiClient.TourPlace tourPlace) {
        if (!hasText(tourPlace.contentId()) || !hasText(tourPlace.name())) {
            return null;
        }

        List<TourApiClient.TourImage> allowedImages = tourPlace.images().stream()
                .filter(Objects::nonNull)
                .filter(image -> hasText(image.imageUrl()))
                .filter(PlaceService::isAllowedImage)
                .sorted(Comparator.comparingInt(TourApiClient.TourImage::sortOrder))
                .limit(MAX_PLACE_IMAGES)
                .toList();
        String thumbnailUrl = allowedImages.stream()
                .map(TourApiClient.TourImage::imageUrl)
                .findFirst()
                .orElse(null);

        Place incoming = new Place(tourPlace.contentId(), tourPlace.name(), tourPlace.region(),
                tourPlace.category(), tourPlace.description(), tourPlace.latitude(), tourPlace.longitude(),
                thumbnailUrl, "KTO", tourPlace.sourceUpdatedAt());
        Place place = places.findByTourContentId(tourPlace.contentId()).orElse(null);
        if (place == null) {
            place = incoming;
        } else {
            place.updateFrom(incoming, tourPlace.sourceUpdatedAt());
        }
        Place saved = places.save(place);

        images.deleteByPlace(saved);
        Set<String> seenUrls = new LinkedHashSet<>();
        allowedImages.stream()
                .filter(image -> seenUrls.add(image.imageUrl()))
                .forEach(image -> images.save(new PlaceImage(saved, image.imageUrl(), image.title(),
                        "KTO", image.sortOrder(), image.copyrightCode())));
        return saved;
    }

    private static boolean isAllowedImage(TourApiClient.TourImage image) {
        return isAllowedCopyright(image.copyrightCode());
    }

    private static boolean isAllowedImage(PlaceImage image) {
        return isAllowedCopyright(image.getCopyrightCode());
    }

    private static boolean isAllowedCopyright(String copyrightCode) {
        return ALLOWED_COPYRIGHT_CODE.equalsIgnoreCase(copyrightCode == null ? "" : copyrightCode.trim());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> T readCached(String key, Class<T> type) {
        if (cache == null) {
            return null;
        }
        try {
            String value = cache.get(key);
            if (!hasText(value)) {
                return null;
            }
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            log.warn("Ignoring place cache read failure: key={}", key);
            return null;
        }
    }

    private void writeCache(String key, Object value, java.time.Duration ttl) {
        if (cache == null) {
            return;
        }
        try {
            cache.put(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Ignoring place cache write failure: key={}", key);
        }
    }

    private static String listCacheKey(String category, String keyword, int page, int size) {
        return "place:list:v5:" + cachePart(category) + ":" + cachePart(keyword) + ":" + page + ":" + size;
    }

    private static String detailCacheKey(String id) {
        return "place:detail:v3:" + cachePart(id);
    }

    private static String cachePart(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
