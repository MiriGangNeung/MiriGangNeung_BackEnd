package com.mirigangneung.course.domain;

import org.junit.jupiter.api.Test;
import com.mirigangneung.place.domain.Place;

import static org.assertj.core.api.Assertions.assertThat;

class CourseStopExternalPlaceTest {
    @Test
    void keepsKakaoPlaceAsCourseOwnedSnapshot() {
        Course course = new Course("day", null, null);
        CourseExternalPlace externalPlace = new CourseExternalPlace(
                "KAKAO",
                "12345",
                "카페 예시",
                "음식점 > 카페",
                "cafe",
                "강릉시 안목동",
                "강릉시 창해로",
                "033-000-0000",
                "https://place.map.kakao.com/12345",
                37.772,
                128.948
        );

        CourseStop stop = new CourseStop(course, externalPlace, 4, false);

        assertThat(stop.getId()).isNull();
        assertThat(stop.isExternal()).isTrue();
        assertThat(stop.getPlace()).isNull();
        assertThat(stop.getExternalPlace()).isSameAs(externalPlace);
        assertThat(stop.getDisplayName()).isEqualTo("카페 예시");
        assertThat(stop.getCategory()).isEqualTo("cafe");
        assertThat(stop.getLatitude()).isEqualTo(37.772);
        assertThat(stop.getLongitude()).isEqualTo(128.948);
        assertThat(stop.getPlaceUrl()).isEqualTo("https://place.map.kakao.com/12345");
    }

    @Test
    void exposesTheEnrichedKakaoUrlForAnOriginalTourismStop() {
        Course course = new Course("day", null, null);
        Place place = new Place("kto-1", "경포해변", "강릉", "nature", "",
                37.8, 128.9, "https://kto/image.jpg", "KTO");
        place.linkKakaoPlace("12345", "https://place.map.kakao.com/12345");

        CourseStop stop = new CourseStop(course, place, 1, true);

        assertThat(stop.getPlaceUrl()).isEqualTo("https://place.map.kakao.com/12345");
    }
}
