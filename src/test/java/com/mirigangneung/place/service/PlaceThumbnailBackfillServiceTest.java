package com.mirigangneung.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirigangneung.infrastructure.image.ImageCacheProperties;
import com.mirigangneung.infrastructure.image.PlaceImageUrlResolver;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.place.repository.PlaceImageRepository;
import com.mirigangneung.place.repository.PlaceRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceThumbnailBackfillServiceTest {

    @Mock private PlaceRepository placeRepository;
    @Mock private PlaceImageRepository placeImageRepository;

    private PlaceThumbnailBackfillService service;

    @BeforeEach
    void setUp() {
        service = new PlaceThumbnailBackfillService(
                placeRepository,
                placeImageRepository,
                new PlaceImageUrlResolver(new ImageCacheProperties(
                        false,
                        "/tmp/miri-images",
                        "http://localhost:8080/media/images",
                        Duration.ofSeconds(3),
                        2_000_000,
                        640,
                        Duration.ofDays(365))));
    }

    @Test
    void fillsMissingThumbnailFromFirstType1PlaceImage() {
        Place place = place("정동진");
        PlaceImage image = new PlaceImage(
                place,
                "https://tong.visitkorea.or.kr/jeongdongjin.jpg",
                "정동진",
                "KTO",
                0,
                "Type1");
        when(placeRepository.findAll()).thenReturn(List.of(place));
        when(placeImageRepository.findByPlaceInOrderBySortOrderAsc(anyList()))
                .thenReturn(List.of(image));

        int updated = service.backfillMissingThumbnails();

        assertThat(updated).isOne();
        assertThat(place.getThumbnailUrl()).isEqualTo(image.getImageUrl());
        verify(placeRepository).saveAll(List.of(place));
    }

    @Test
    void leavesPlaceWithoutType1ImageUnchanged() {
        Place place = place("이미지 없음");
        PlaceImage image = new PlaceImage(
                place,
                "https://example.com/type2.jpg",
                "이미지 없음",
                "KTO",
                0,
                "Type2");
        when(placeRepository.findAll()).thenReturn(List.of(place));
        when(placeImageRepository.findByPlaceInOrderBySortOrderAsc(anyList()))
                .thenReturn(List.of(image));

        int updated = service.backfillMissingThumbnails();

        assertThat(updated).isZero();
        assertThat(place.getThumbnailUrl()).isNull();
        verify(placeRepository, never()).saveAll(anyList());
    }

    private static Place place(String name) {
        return new Place(
                name,
                name,
                "강원특별자치도 강릉시",
                "nature",
                null,
                37.75,
                128.90,
                null,
                "KTO");
    }
}
