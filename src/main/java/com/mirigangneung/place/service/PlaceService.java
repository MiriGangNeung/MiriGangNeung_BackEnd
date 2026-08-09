package com.mirigangneung.place.service;

import com.mirigangneung.common.error.ApiException;
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

@Service
public class PlaceService {
    private static final Logger log = LoggerFactory.getLogger(PlaceService.class);

    private final PlaceRepository places;
    private final PlaceImageRepository images;
    private final TourApiClient tour;

    public PlaceService(PlaceRepository places, PlaceImageRepository images, TourApiClient tour) {
        this.places = places;
        this.images = images;
        this.tour = tour;
    }

    @Transactional
    public PlacePageResponse search(String category, String keyword, int page, int size) {
        String normalizedKeyword = keyword == null ? "" : keyword;
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
        return PlacePageResponse.from(result);
    }

    @Transactional
    public PlaceDetailResponse detail(String id) {
        Place place = existingOrFetch(id);
        return PlaceDetailResponse.fromImages(place, images.findByPlaceOrderBySortOrderAsc(place));
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
        Place place = places.findByTourContentId(tourPlace.contentId()).orElse(incoming);
        place.updateFrom(incoming, tourPlace.sourceUpdatedAt());
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
}
