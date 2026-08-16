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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class PlaceService {
    private static final Logger log = LoggerFactory.getLogger(PlaceService.class);

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

        try {
            tour.search(keyword, category, page, size).forEach(this::upsert);
        } catch (ApiException e) {
            if (!"TOUR_API_ERROR".equals(e.getCode())) {
                throw e;
            }
            log.warn("Using local place data because tourism search failed");
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
        PlacePageResponse response = PlacePageResponse.from(result);
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

        Place place = existingOrFetch(id);
        PlaceDetailResponse response = PlaceDetailResponse.fromImages(place, images.findByPlaceOrderBySortOrderAsc(place));
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

    private Place existingOrFetch(String id) {
        try {
            return find(id);
        } catch (ApiException e) {
            if (!"PLACE_NOT_FOUND".equals(e.getCode())) {
                throw e;
            }
            return tour.find(id).map(this::upsert).orElseThrow(this::notFound);
        }
    }

    private ApiException notFound() {
        return new ApiException("PLACE_NOT_FOUND", HttpStatus.NOT_FOUND, "관광지를 찾을 수 없습니다.");
    }

    private Place upsert(TourApiClient.TourPlace tourPlace) {
        if (!hasText(tourPlace.contentId()) || !hasText(tourPlace.name())) {
            return null;
        }

        Place incoming = new Place(tourPlace.contentId(), tourPlace.name(), tourPlace.region(),
                tourPlace.category(), tourPlace.description(), tourPlace.latitude(), tourPlace.longitude(),
                tourPlace.thumbnailUrl(), "KTO");
        Place place = places.findByTourContentId(tourPlace.contentId()).orElse(null);
        if (place == null) {
            place = incoming;
        } else {
            place.updateFrom(incoming, tourPlace.sourceUpdatedAt());
        }
        Place saved = places.save(place);

        if (tourPlace.images() != null && !tourPlace.images().isEmpty()) {
            images.deleteByPlace(saved);
            Set<String> seenUrls = new LinkedHashSet<>();
            tourPlace.images().stream()
                    .filter(Objects::nonNull)
                    .filter(image -> hasText(image.imageUrl()))
                    .sorted(Comparator.comparingInt(TourApiClient.TourImage::sortOrder))
                    .filter(image -> seenUrls.add(image.imageUrl()))
                    .forEach(image -> images.save(new PlaceImage(saved, image.imageUrl(), image.title(),
                            "KTO", image.sortOrder(), image.copyrightCode())));
        }
        return saved;
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
        return "place:list:v1:" + cachePart(category) + ":" + cachePart(keyword) + ":" + page + ":" + size;
    }

    private static String detailCacheKey(String id) {
        return "place:detail:v1:" + cachePart(id);
    }

    private static String cachePart(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
