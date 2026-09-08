package com.mirigangneung.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class KakaoPlaceMappingCatalogTest {

    @Test
    void loadsAllVisiblePlaceMappingsFromTheVersionedCsv() {
        KakaoPlaceMappingCatalog catalog = new KakaoPlaceMappingCatalog(
                new ClassPathResource("data/kakao-place-mappings.csv"));

        Map<String, KakaoPlaceMapping> mappings = catalog.mappings().stream()
                .collect(Collectors.toMap(KakaoPlaceMapping::tourContentId, Function.identity()));

        assertThat(mappings).hasSize(69);
        assertThat(mappings.get("3545967").kakaoPlaceUrl())
                .isEqualTo("https://place.map.kakao.com/11164998");
        assertThat(mappings.get("2628994").kakaoPlaceId()).isNull();
        assertThat(mappings.get("2628994").kakaoPlaceUrl()).isNull();
    }
}
