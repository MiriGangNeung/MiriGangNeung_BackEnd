package com.mirigangneung.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.repository.PlaceRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KakaoPlaceMappingServiceTest {

    @Mock private PlaceRepository placeRepository;
    @Mock private KakaoPlaceMappingCatalog mappingCatalog;

    private KakaoPlaceMappingService service;

    @BeforeEach
    void setUp() {
        service = new KakaoPlaceMappingService(placeRepository, mappingCatalog);
    }

    @Test
    void appliesCuratedLinksAndExplicitlyClearsSuppressedPlaces() {
        Place mapped = place("100", "안목해변");
        Place suppressed = place("200", "강릉 명주동 거리");
        suppressed.linkKakaoPlace("old-id", "https://place.map.kakao.com/old-id");
        when(mappingCatalog.mappings()).thenReturn(List.of(
                new KakaoPlaceMapping("100", "kakao-100", "https://place.map.kakao.com/kakao-100"),
                new KakaoPlaceMapping("200", null, null),
                new KakaoPlaceMapping("missing", "kakao-missing", "https://place.map.kakao.com/kakao-missing")));
        when(placeRepository.findAllByTourContentIdIn(List.of("100", "200", "missing")))
                .thenReturn(List.of(mapped, suppressed));

        KakaoPlaceMappingService.MappingResult result = service.applyMappings();

        assertThat(result.configuredMappings()).isEqualTo(3);
        assertThat(result.updatedPlaces()).isEqualTo(2);
        assertThat(result.suppressedPlaces()).isEqualTo(1);
        assertThat(result.missingPlaces()).isEqualTo(1);
        assertThat(mapped.getKakaoPlaceId()).isEqualTo("kakao-100");
        assertThat(mapped.getKakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/kakao-100");
        assertThat(suppressed.getKakaoPlaceId()).isNull();
        assertThat(suppressed.getKakaoPlaceUrl()).isNull();
        verify(placeRepository).saveAll(List.of(mapped, suppressed));
    }

    @Test
    void doesNotWriteWhenDatabaseAlreadyMatchesTheCuratedCatalog() {
        Place place = place("100", "안목해변");
        place.linkKakaoPlace("kakao-100", "https://place.map.kakao.com/kakao-100");
        when(mappingCatalog.mappings()).thenReturn(List.of(
                new KakaoPlaceMapping("100", "kakao-100", "https://place.map.kakao.com/kakao-100")));
        when(placeRepository.findAllByTourContentIdIn(List.of("100"))).thenReturn(List.of(place));

        KakaoPlaceMappingService.MappingResult result = service.applyMappings();

        assertThat(result.updatedPlaces()).isZero();
        assertThat(result.suppressedPlaces()).isZero();
        verify(placeRepository, never()).saveAll(anyList());
    }

    private static Place place(String contentId, String name) {
        return new Place(
                contentId,
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
