package com.mirigangneung.place.service;

import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.infrastructure.tourapi.TourApiClient;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.place.dto.PlaceDetailResponse;
import com.mirigangneung.place.repository.PlaceImageRepository;
import com.mirigangneung.place.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PlaceServiceTest {
    private PlaceRepository places;
    private PlaceImageRepository images;
    private TourApiClient tour;
    private PlaceService service;

    @BeforeEach
    void setUp() {
        places = mock(PlaceRepository.class);
        images = mock(PlaceImageRepository.class);
        tour = mock(TourApiClient.class);
        service = new PlaceService(places, images, tour);
        when(places.save(any(Place.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void storesUpstreamSourceDateAndReplacesImagesOnEveryUpsert() {
        OffsetDateTime sourceDate = OffsetDateTime.of(2026, 8, 9, 12, 0, 0, 0, ZoneOffset.ofHours(9));
        TourApiClient.TourPlace remote = new TourApiClient.TourPlace(
                "100", "경포대", "강릉시", "nature", "호수 전망", 37.8, 128.9,
                "https://img.test/one.jpg",
                List.of(new TourApiClient.TourImage("https://img.test/one.jpg", "대표", "Y", 0)),
                sourceDate);
        when(tour.search(null, null, 0, 20)).thenReturn(List.of(remote));
        when(places.findByTourContentId("100")).thenReturn(Optional.empty());
        when(places.findByRegionContainingAndNameContaining(eq("강릉"), eq(""), any()))
                .thenReturn(Page.empty());

        service.search(null, null, 0, 20);
        service.search(null, null, 0, 20);

        var placeCaptor = org.mockito.ArgumentCaptor.forClass(Place.class);
        verify(places, times(2)).save(placeCaptor.capture());
        assertThat(placeCaptor.getAllValues()).allSatisfy(place -> {
            assertThat(place.getTourContentId()).isEqualTo("100");
            assertThat(place.getSourceUpdatedAt()).isEqualTo(sourceDate);
        });
        verify(images, times(2)).deleteByPlace(any(Place.class));
        verify(images, times(2)).save(any(PlaceImage.class));
    }

    @Test
    void filtersByTheSameNormalizedCategoryUsedForPersistence() {
        when(tour.search("", "food", 0, 20)).thenReturn(List.of());
        when(places.findByCategoryContainingAndNameContaining(eq("food"), eq(""), any()))
                .thenReturn(Page.empty());

        service.search("food", "", 0, 20);

        verify(places).findByCategoryContainingAndNameContaining(eq("food"), eq(""), any());
        verify(places, never()).findByRegionContainingAndNameContaining(any(), any(), any());
    }

    @Test
    void returnsStructuredImageMetadataAndLegacyUrls() {
        Place place = withId(new Place("100", "경포대", "강릉시", "nature", "설명",
                37.8, 128.9, "https://img.test/one.jpg", "KTO"));
        PlaceImage image = new PlaceImage(place, "https://img.test/one.jpg", "대표", "KTO", 0, "Y");
        when(places.findById(place.getId())).thenReturn(Optional.of(place));
        when(images.findByPlaceOrderBySortOrderAsc(place)).thenReturn(List.of(image));

        PlaceDetailResponse result = service.detail(place.getId().toString());

        assertThat(result.imageUrls()).containsExactly("https://img.test/one.jpg");
        assertThat(result.images()).singleElement().satisfies(detail -> {
            assertThat(detail.imageUrl()).isEqualTo("https://img.test/one.jpg");
            assertThat(detail.title()).isEqualTo("대표");
            assertThat(detail.copyrightCode()).isEqualTo("Y");
        });
    }

    @Test
    void fallsBackToLocalPageWhenTourSearchFails() {
        Place local = withId(new Place("100", "경포대", "강릉시", "nature", "설명",
                37.8, 128.9, null, "KTO"));
        Page<Place> localPage = new PageImpl<>(List.of(local), PageRequest.of(0, 20), 1);
        when(tour.search(null, null, 0, 20))
                .thenThrow(new ApiException("TOUR_API_ERROR", HttpStatus.BAD_GATEWAY, "upstream"));
        when(places.findByRegionContainingAndNameContaining(eq("강릉"), eq(""), any()))
                .thenReturn(localPage);

        var result = service.search(null, null, 0, 20);

        assertThat(result.content()).singleElement().satisfies(place ->
                assertThat(place.name()).isEqualTo("경포대"));
    }

    @Test
    void returnsDetailWithoutImages() {
        Place place = withId(new Place("100", "경포대", "강릉시", "nature", "설명",
                37.8, 128.9, null, "KTO"));
        when(places.findById(place.getId())).thenReturn(Optional.of(place));
        when(images.findByPlaceOrderBySortOrderAsc(place)).thenReturn(List.of());

        assertThat(service.detail(place.getId().toString()).images()).isEmpty();
    }

    private static Place withId(Place place) {
        ReflectionTestUtils.setField(place, "id", UUID.randomUUID());
        return place;
    }
}
