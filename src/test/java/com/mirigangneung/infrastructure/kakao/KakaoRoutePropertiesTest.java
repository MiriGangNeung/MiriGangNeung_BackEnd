package com.mirigangneung.infrastructure.kakao;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoRoutePropertiesTest {
    @Test
    void defaultsWalkingRoutesToTheKakaoDevelopersHost() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        MutablePropertySources sources = new MutablePropertySources();
        loader.load("application", new ClassPathResource("application.yml"))
                .forEach(sources::addLast);
        PropertySourcesPropertyResolver properties = new PropertySourcesPropertyResolver(sources);

        assertThat(properties.getProperty("kakao.api.base-url"))
                .isEqualTo("https://dapi.kakao.com");
        assertThat(properties.getProperty("kakao.local.base-url"))
                .isEqualTo("https://dapi.kakao.com");
    }
}
