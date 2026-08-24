package com.mirigangneung.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.mirigangneung.infrastructure.tourapi.TourApiClient;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.place.repository.PlaceImageRepository;
import com.mirigangneung.place.repository.PlaceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class PlaceCatalogSyncTransactionTest {

    @Autowired private PlaceCatalogSyncService service;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private PlaceImageRepository placeImageRepository;

    @MockitoBean private TourApiClient tourApiClient;
    @MockitoBean private TourismPhotoMatcher tourismPhotoMatcher;

    @Test
    void replacesExistingImagesWithoutRequiringCallerManagedTransaction() {
        Place existing = placeRepository.save(new Place(
                "transaction-test-place",
                "기존 장소",
                "강릉시",
                "nature",
                "기존 설명",
                37.75,
                128.90,
                null,
                "KTO"));
        placeImageRepository.save(new PlaceImage(
                existing,
                "https://img.test/forbidden.jpg",
                "교체할 이미지",
                "KTO",
                0,
                "Type3"));
        TourApiClient.TourPlace place = new TourApiClient.TourPlace(
                "transaction-test-place",
                "트랜잭션 테스트 장소",
                "강원특별자치도 강릉시",
                "nature",
                "설명",
                37.75,
                128.90,
                "https://img.test/place.jpg",
                List.of(new TourApiClient.TourImage(
                        "https://img.test/place.jpg", "대표", "Type1", 0)),
                OffsetDateTime.parse("2026-08-24T00:00:00Z"));
        when(tourApiClient.searchSummaries(null, null, 0, 1000)).thenReturn(List.of(place));
        when(tourismPhotoMatcher.findImageUrls(anyList())).thenReturn(Map.of());

        assertThatCode(service::synchronizeAll).doesNotThrowAnyException();

        assertThat(placeRepository.findByTourContentId("transaction-test-place")).isPresent();
        assertThat(placeImageRepository.count()).isEqualTo(1);
    }
}
