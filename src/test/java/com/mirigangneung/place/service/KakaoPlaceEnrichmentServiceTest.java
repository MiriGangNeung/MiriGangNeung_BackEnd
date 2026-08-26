package com.mirigangneung.place.service;

import com.mirigangneung.infrastructure.kakao.KakaoLocalClient;
import com.mirigangneung.infrastructure.kakao.KakaoLocalProperties;
import com.mirigangneung.place.domain.Place;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KakaoPlaceEnrichmentServiceTest {
    @Mock
    private KakaoLocalClient localClient;

    @Test
    void linksTheKakaoPlaceWhenNormalizedNamesMatch() {
        Place place = new Place(
                "kto-1", "강릉 선교장", "강원특별자치도 강릉시", "culture", "",
                37.75, 128.90, "https://kto/image.jpg", "KTO");
        when(localClient.searchByKeyword(eq("강릉 선교장"), eq(128.90), eq(37.75),
                eq(2_000), eq(0), eq(15)))
                .thenReturn(List.of(kakaoPlace("kakao-1", "선교장", 37.751, 128.901)));

        KakaoPlaceEnrichmentService service = new KakaoPlaceEnrichmentService(
                localClient,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15));

        var result = service.enrich(List.of(place));

        assertThat(result.matchedPlaces()).isEqualTo(1);
        assertThat(place.getKakaoPlaceId()).isEqualTo("kakao-1");
        assertThat(place.getKakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/kakao-1");
        verify(localClient).searchByKeyword("강릉 선교장", 128.90, 37.75, 2_000, 0, 15);
    }

    @Test
    void doesNotLinkAPlaceWhenNormalizedNamesDoNotMatch() {
        Place place = new Place(
                "kto-2", "경포대", "강원특별자치도 강릉시", "nature", "",
                37.80, 128.90, null, "KTO");
        when(localClient.searchByKeyword(anyString(), eq(128.90), eq(37.80),
                anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(kakaoPlace("kakao-2", "경포해변", 37.801, 128.901)));

        KakaoPlaceEnrichmentService service = new KakaoPlaceEnrichmentService(
                localClient,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15));

        var result = service.enrich(List.of(place));

        assertThat(result.matchedPlaces()).isZero();
        assertThat(place.getKakaoPlaceUrl()).isNull();
    }

    @Test
    void doesNotLinkAnExactNameFoundOutsideTheConfiguredRadius() {
        Place place = new Place(
                "kto-3", "오죽헌", "강원특별자치도 강릉시", "culture", "",
                37.80, 128.90, null, "KTO");
        when(localClient.searchByKeyword(anyString(), eq(128.90), eq(37.80),
                anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(kakaoPlace("kakao-3", "오죽헌", 37.85, 128.90)));

        KakaoPlaceEnrichmentService service = new KakaoPlaceEnrichmentService(
                localClient,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15));

        var result = service.enrich(List.of(place));

        assertThat(result.matchedPlaces()).isZero();
        assertThat(place.getKakaoPlaceUrl()).isNull();
    }

    @Test
    void skipsPlacesThatAlreadyHaveAKakaoLink() {
        Place place = new Place(
                "kto-4", "안목해변", "강원특별자치도 강릉시", "nature", "",
                37.80, 128.90, null, "KTO");
        place.linkKakaoPlace("already-linked", "https://place.map.kakao.com/already-linked");

        KakaoPlaceEnrichmentService service = new KakaoPlaceEnrichmentService(
                localClient,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15));

        var result = service.enrich(List.of(place));

        assertThat(result.skippedPlaces()).isEqualTo(1);
        verifyNoInteractions(localClient);
    }

    private static KakaoLocalClient.NearbyPlace kakaoPlace(String id, String name, double lat, double lon) {
        return new KakaoLocalClient.NearbyPlace(
                id, name, "관광명소", "AT4", "강릉", "강릉", "",
                "https://place.map.kakao.com/" + id, lat, lon, null);
    }
}
