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

@SpringBootTest(properties = {
        "tour.api.sync-on-startup=false",
        "image.cache.enabled=false"
})
class PlaceCatalogSyncTransactionTest {

    @Autowired private PlaceCatalogSyncService service;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private PlaceImageRepository placeImageRepository;

    @MockitoBean private TourApiClient tourApiClient;
    @MockitoBean private TourismPhotoMatcher tourismPhotoMatcher;
    @MockitoBean private ImageUrlValidator imageUrlValidator;

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
        when(imageUrlValidator.isUsable("https://img.test/place.jpg")).thenReturn(true);

        assertThatCode(service::synchronizeAll).doesNotThrowAnyException();

        assertThat(placeRepository.findByTourContentId("transaction-test-place")).isPresent();
        assertThat(placeImageRepository.count()).isEqualTo(1);
    }

    @Test
    void deletesStoredFoodPlacesAndTheirImagesDuringSynchronization() {
        Place food = placeRepository.save(new Place(
                "food-to-delete",
                "삭제할 음식점",
                "강원특별자치도 강릉시",
                "food",
                "설명",
                37.75,
                128.90,
                null,
                "KTO"));
        placeImageRepository.save(new PlaceImage(
                food,
                "https://img.test/food.jpg",
                "음식점",
                "KTO",
                0,
                "Type1"));
        when(tourApiClient.searchSummaries(null, null, 0, 1000)).thenReturn(List.of());

        PlaceCatalogSyncService.SyncResult result = service.synchronizeAll();

        assertThat(result.deletedFoodPlaces()).isEqualTo(1);
        assertThat(placeRepository.findByTourContentId("food-to-delete")).isEmpty();
        assertThat(placeImageRepository.findByPlaceOrderBySortOrderAsc(food)).isEmpty();
    }

    @Test
    void deletesLegacyPhotoGalleryOnlyCardsWithoutCreatingNewCards() {
        Place staleGallery = placeRepository.save(new Place(
                "gallery:old-food",
                "감자옹심이",
                "강원특별자치도 강릉시",
                "gallery",
                "PhotoGalleryService1 검수용 카드",
                null,
                null,
                null,
                "KTO_PHOTO_GALLERY"));
        placeImageRepository.save(new PlaceImage(
                staleGallery,
                "https://img.test/old-food.jpg",
                "감자옹심이",
                "KTO_PHOTO_GALLERY",
                0,
                "Type1"));
        TourApiClient.TourPlace knownPlace = new TourApiClient.TourPlace(
                "known-place",
                "경포대",
                "강원특별자치도 강릉시",
                "nature",
                "설명",
                37.75,
                128.90,
                null,
                List.of(),
                OffsetDateTime.parse("2026-08-24T00:00:00Z"));
        when(tourApiClient.searchSummaries(null, null, 0, 1000)).thenReturn(List.of(knownPlace));
        when(tourismPhotoMatcher.findImageUrls(anyList())).thenReturn(Map.of());

        PlaceCatalogSyncService.SyncResult result = service.synchronizeAll();

        assertThat(placeRepository.findByTourContentId("gallery:old-food")).isEmpty();
        assertThat(result.deletedGalleryOnlyCards()).isEqualTo(1);
        assertThat(placeRepository.findBySource("KTO_PHOTO_GALLERY")).isEmpty();
    }
}
