package com.mirigangneung.place.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceShortDescriptionCatalogTest {
    @Test
    void returnsCuratedDescriptionForEveryCanonicalPlace() {
        assertThat(PlaceShortDescriptionCatalog.get("강문해변"))
                .isEqualTo("푸른 동해와 아담한 강문항이 나란히 어우러진 해변입니다. 포토존과 해변 산책을 즐기며 강릉 바다의 여유를 느껴보세요.");
        assertThat(PlaceShortDescriptionCatalog.get("강릉 경포대"))
                .isEqualTo("경포호 북쪽 언덕에 자리한 관동팔경의 누각이자 보물입니다. 고즈넉한 누각에서 경포호와 주변 풍경의 운치를 느껴보세요.");
        assertThat(PlaceShortDescriptionCatalog.get("경포해수욕장"))
                .isEqualTo("넓은 백사장과 울창한 송림이 어우러진 강릉의 대표 해변입니다. 시원한 동해와 솔숲을 함께 즐기며 여유롭게 쉬어가세요.");
        assertThat(PlaceShortDescriptionCatalog.get("정동진"))
                .isNotEqualTo(PlaceShortDescriptionCatalog.get("정동진해변"));
        assertThat(PlaceShortDescriptionCatalog.get("임당동 성당"))
                .isEqualTo(PlaceShortDescriptionCatalog.get("강릉 임당동성당"));
        assertThat(PlaceShortDescriptionCatalog.get("강릉 솔향수목원"))
                .isEqualTo(PlaceShortDescriptionCatalog.get("강릉솔향수목원"));
    }

    @Test
    void returnsNullForUnknownPlaceInsteadOfGuessing() {
        assertThat(PlaceShortDescriptionCatalog.get("강릉 녹색도시체험센터")).isNull();
        assertThat(PlaceShortDescriptionCatalog.get(null)).isNull();
    }
}
