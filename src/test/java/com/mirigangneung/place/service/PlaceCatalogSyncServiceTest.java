package com.mirigangneung.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirigangneung.infrastructure.tourapi.TourApiClient;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.place.repository.PlaceImageRepository;
import com.mirigangneung.place.repository.PlaceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlaceCatalogSyncServiceTest {

    @Mock private TourApiClient tourApiClient;
    @Mock private PlaceRepository placeRepository;
    @Mock private PlaceImageRepository placeImageRepository;
    @Mock private TourismPhotoMatcher tourismPhotoMatcher;
    @Mock private PlaceCatalogCleanupService cleanupService;
    @Mock private ImageUrlValidator imageUrlValidator;

    private PlaceCatalogSyncService service;

    @BeforeEach
    void setUp() {
        service = new PlaceCatalogSyncService(
                tourApiClient,
                placeRepository,
                placeImageRepository,
                tourismPhotoMatcher,
                cleanupService,
                imageUrlValidator,
                2);
    }

    @Test
    void synchronizesEverySummaryPageAndPersistsUpToFiveType1Images() {
        TourApiClient.TourPlace first = tourPlace("1", "안목해변", List.of());
        TourApiClient.TourPlace second = tourPlace(
                "2",
                "경포해변",
                List.of(new TourApiClient.TourImage("https://kto/2.jpg", "대표", "Type1", 0)));
        TourApiClient.TourPlace third = tourPlace("3", "강릉선교장", List.of());

        when(tourApiClient.searchSummaries(null, null, 0, 2)).thenReturn(List.of(first, second));
        when(tourApiClient.searchSummaries(null, null, 1, 2)).thenReturn(List.of(third));
        when(placeRepository.findByTourContentId(any())).thenReturn(Optional.empty());

        AtomicInteger sequence = new AtomicInteger();
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            ReflectionTestUtils.setField(place, "id", new UUID(0, sequence.incrementAndGet()));
            return place;
        });
        when(placeImageRepository.findByPlaceOrderBySortOrderAsc(any())).thenReturn(List.of());
        when(imageUrlValidator.isUsable(anyString())).thenReturn(true);
        when(imageUrlValidator.isUsable("https://gallery/6.jpg")).thenReturn(false);
        when(tourismPhotoMatcher.findImageUrls(anyList())).thenAnswer(invocation -> {
            List<Place> places = invocation.getArgument(0);
            Place anmok = places.stream()
                    .filter(place -> "1".equals(place.getTourContentId()))
                    .findFirst()
                    .orElseThrow();
            return Map.of(
                    anmok.getId(),
                    List.of(
                            "https://gallery/1.jpg",
                            "https://gallery/2.jpg",
                            "https://gallery/3.jpg",
                            "https://gallery/4.jpg",
                            "https://gallery/5.jpg",
                            "https://gallery/6.jpg"));
        });

        PlaceCatalogSyncService.SyncResult result = service.synchronizeAll();

        assertThat(result.fetchedPlaces()).isEqualTo(3);
        assertThat(result.excludedByCategory()).isZero();
        assertThat(result.deletedFoodPlaces()).isZero();
        assertThat(result.savedPlaces()).isEqualTo(3);
        assertThat(result.placesWithImages()).isEqualTo(2);
        assertThat(result.rejectedImageUrls()).isEqualTo(1);
        verify(tourApiClient).searchSummaries(null, null, 0, 2);
        verify(tourApiClient).searchSummaries(null, null, 1, 2);
        verify(tourApiClient, never()).searchSummaries(null, null, 2, 2);
        verify(placeRepository, times(3)).save(any(Place.class));
        verify(placeImageRepository, times(6)).save(any(PlaceImage.class));
    }

    private static TourApiClient.TourPlace tourPlace(
            String contentId,
            String name,
            List<TourApiClient.TourImage> images) {
        return new TourApiClient.TourPlace(
                contentId,
                name,
                "강원특별자치도 강릉시",
                "nature",
                null,
                37.75,
                128.90,
                null,
                images,
                OffsetDateTime.parse("2026-08-24T00:00:00Z"));
    }
}
