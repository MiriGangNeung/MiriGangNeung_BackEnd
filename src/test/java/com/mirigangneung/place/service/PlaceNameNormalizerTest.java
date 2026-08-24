package com.mirigangneung.place.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceNameNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "'강릉 선교장', '선교장'",
            "'정동심곡 바다부채길', '정동심곡바다부채길'",
            "'남대천(강릉)', '남대천'",
            "'경포해수욕장', '경포해변'",
            "'강릉통일공원안보전시관', '강릉통일공원'",
            "'등명낙가사(강릉)', '등명락가사'",
            "'구룡폭포(소금강)', '구룡폭포오대산'"
    })
    void normalizesKnownPlaceNameVariants(String input, String expected) {
        assertThat(PlaceNameNormalizer.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "'경포대', '경포해변'",
            "'경포호', '경포해변'",
            "'사천해변', '사천진해변'",
            "'안목해변', '안목커피거리'"
    })
    void keepsDifferentNearbyPlacesSeparate(String first, String second) {
        assertThat(PlaceNameNormalizer.normalize(first))
                .isNotEqualTo(PlaceNameNormalizer.normalize(second));
    }
}
