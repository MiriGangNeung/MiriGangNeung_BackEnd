package com.mirigangneung.infrastructure.kakao;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoLocalPropertiesTest {

    @Test
    void defaultRectCoversGangneungEdgePlaces() {
        KakaoLocalProperties properties = new KakaoLocalProperties(
                "https://dapi.kakao.com", "key", Duration.ofSeconds(3), 2_000, 15, null);

        assertThat(properties.allSearchRect()).isEqualTo(KakaoLocalProperties.DEFAULT_GANGNEUNG_RECT);
        // 안반데기(왕산면), 노추산(왕산면 남단), 옥계해변(옥계면), 주문진(북단), 경포대(시내)
        assertThat(contains(properties.allSearchRect(), 128.7391, 37.6229)).isTrue();
        assertThat(contains(properties.allSearchRect(), 128.7630, 37.5860)).isTrue();
        assertThat(contains(properties.allSearchRect(), 129.0510, 37.6330)).isTrue();
        assertThat(contains(properties.allSearchRect(), 128.8210, 37.8920)).isTrue();
        assertThat(contains(properties.allSearchRect(), 128.8965, 37.7952)).isTrue();
    }

    @Test
    void configuredRectOverridesDefault() {
        KakaoLocalProperties properties = new KakaoLocalProperties(
                "https://dapi.kakao.com", "key", Duration.ofSeconds(3), 2_000, 15, " 1,2,3,4 ");

        assertThat(properties.allSearchRect()).isEqualTo("1,2,3,4");
    }

    private static boolean contains(String rect, double longitude, double latitude) {
        String[] parts = rect.split(",");
        double left = Double.parseDouble(parts[0]);
        double top = Double.parseDouble(parts[1]);
        double right = Double.parseDouble(parts[2]);
        double bottom = Double.parseDouble(parts[3]);
        return longitude >= left && longitude <= right && latitude >= bottom && latitude <= top;
    }
}
