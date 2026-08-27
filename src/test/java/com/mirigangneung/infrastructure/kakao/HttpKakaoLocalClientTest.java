package com.mirigangneung.infrastructure.kakao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirigangneung.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class HttpKakaoLocalClientTest {
    private static final String RESPONSE = """
            {
              "meta": {"is_end": true},
              "documents": [
                {
                  "id": "123",
                  "place_name": "카페 예시",
                  "category_name": "음식점 > 카페",
                  "category_group_code": "CE7",
                  "address_name": "강릉시 안목동",
                  "road_address_name": "강릉시 창해로",
                  "phone": "033-000-0000",
                  "place_url": "https://place.map.kakao.com/123",
                  "x": "128.948",
                  "y": "37.772",
                  "distance": "450"
                }
              ]
            }
            """;

    @Test
    void requestsCategoryResultsWithGangneungRadiusAndDistanceSort() {
        Fixture fixture = fixture("secret");
        fixture.server.expect(once(), request -> {
            assertThat(request.getMethod()).isEqualTo(GET);
            assertThat(request.getURI().getPath()).isEqualTo("/v2/local/search/category.json");
            var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            assertThat(query.getFirst("category_group_code")).isEqualTo("CE7");
            assertThat(query.getFirst("x")).isEqualTo("128.948");
            assertThat(query.getFirst("y")).isEqualTo("37.772");
            assertThat(query.getFirst("radius")).isEqualTo("2000");
            assertThat(query.getFirst("page")).isEqualTo("2");
            assertThat(query.getFirst("size")).isEqualTo("15");
            assertThat(query.getFirst("sort")).isEqualTo("distance");
        }).andExpect(header("Authorization", "KakaoAK secret"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        List<KakaoLocalClient.NearbyPlace> result = fixture.client.searchByCategory(
                128.948, 37.772, "CE7", 2_000, 1, 15);

        assertThat(result).singleElement().satisfies(place -> {
            assertThat(place.externalPlaceId()).isEqualTo("123");
            assertThat(place.name()).isEqualTo("카페 예시");
            assertThat(place.categoryCode()).isEqualTo("CE7");
            assertThat(place.latitude()).isEqualTo(37.772);
            assertThat(place.longitude()).isEqualTo(128.948);
            assertThat(place.distanceMeters()).isEqualTo(450);
        });
        fixture.server.verify();
    }

    @Test
    void requestsCategoryResultsInsideGangneungRectangleAndPreservesPageMetadata() {
        Fixture fixture = fixture("secret");
        fixture.server.expect(once(), request -> {
            assertThat(request.getMethod()).isEqualTo(GET);
            assertThat(request.getURI().getPath()).isEqualTo("/v2/local/search/category.json");
            var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            assertThat(query.getFirst("category_group_code")).isEqualTo("CE7");
            assertThat(query.getFirst("rect")).isEqualTo("128.70,37.95,129.05,37.65");
            assertThat(query.getFirst("page")).isEqualTo("2");
            assertThat(query.getFirst("size")).isEqualTo("15");
            assertThat(query.getFirst("sort")).isEqualTo("accuracy");
        }).andExpect(header("Authorization", "KakaoAK secret"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        KakaoLocalClient.SearchPage result = fixture.client.searchByCategoryInRect(
                "128.70,37.95,129.05,37.65", "CE7", 1, 15);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.isEnd()).isTrue();
        assertThat(result.places()).singleElement().satisfies(place ->
                assertThat(place.distanceMeters()).isEqualTo(450));
        fixture.server.verify();
    }

    @Test
    void requestsKeywordResultsInsideGangneungRectangle() {
        Fixture fixture = fixture("secret");
        fixture.server.expect(once(), request -> {
            assertThat(request.getMethod()).isEqualTo(GET);
            assertThat(request.getURI().getPath()).isEqualTo("/v2/local/search/keyword.json");
            var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            assertThat(URLDecoder.decode(query.getFirst("query"), StandardCharsets.UTF_8))
                    .isEqualTo("테라로사");
            assertThat(query.getFirst("category_group_code")).isEqualTo("CE7");
            assertThat(query.getFirst("rect")).isEqualTo("128.70,37.95,129.05,37.65");
            assertThat(query.getFirst("page")).isEqualTo("1");
            assertThat(query.getFirst("size")).isEqualTo("15");
            assertThat(query.getFirst("sort")).isEqualTo("accuracy");
        }).andExpect(header("Authorization", "KakaoAK secret"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        KakaoLocalClient.SearchPage result = fixture.client.searchByKeywordInRect(
                "테라로사", "128.70,37.95,129.05,37.65", "CE7", 0, 15);

        assertThat(result.page()).isZero();
        assertThat(result.places()).hasSize(1);
        fixture.server.verify();
    }

    @Test
    void requestsKeywordResultsForKakaoPlaceEnrichment() {
        Fixture fixture = fixture("secret");
        fixture.server.expect(once(), request -> {
            assertThat(request.getMethod()).isEqualTo(GET);
            assertThat(request.getURI().getPath()).isEqualTo("/v2/local/search/keyword.json");
            var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            assertThat(URLDecoder.decode(query.getFirst("query"), StandardCharsets.UTF_8))
                    .isEqualTo("강릉 선교장");
            assertThat(query.getFirst("x")).isEqualTo("128.948");
            assertThat(query.getFirst("y")).isEqualTo("37.772");
            assertThat(query.getFirst("radius")).isEqualTo("2000");
            assertThat(query.getFirst("page")).isEqualTo("1");
            assertThat(query.getFirst("size")).isEqualTo("15");
            assertThat(query.getFirst("sort")).isEqualTo("distance");
        }).andExpect(header("Authorization", "KakaoAK secret"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        List<KakaoLocalClient.NearbyPlace> result = fixture.client.searchByKeyword(
                "강릉 선교장", 128.948, 37.772, 2_000, 0, 15);

        assertThat(result).singleElement().satisfies(place -> {
            assertThat(place.externalPlaceId()).isEqualTo("123");
            assertThat(place.name()).isEqualTo("카페 예시");
            assertThat(place.placeUrl()).isEqualTo("https://place.map.kakao.com/123");
        });
        fixture.server.verify();
    }

    @Test
    void rejectsMissingKeyBeforeCallingKakao() {
        Fixture fixture = fixture("");

        assertThatThrownBy(() -> fixture.client.searchByCategory(
                128.948, 37.772, "FD6", 2_000, 0, 15))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("KAKAO_API_NOT_CONFIGURED"));
        fixture.server.verify();
    }

    @Test
    void mapsUpstreamFailureToApiError() {
        Fixture fixture = fixture("secret");
        fixture.server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/v2/local/search/category.json")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        assertThatThrownBy(() -> fixture.client.searchByCategory(
                128.948, 37.772, "FD6", 2_000, 0, 15))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("KAKAO_API_ERROR"));
        fixture.server.verify();
    }

    private Fixture fixture(String key) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoLocalClient client = new HttpKakaoLocalClient(
                new KakaoLocalProperties("https://example.test", key, Duration.ofSeconds(2), 2_000, 15),
                builder.build(),
                new ObjectMapper());
        return new Fixture(client, server);
    }

    private record Fixture(KakaoLocalClient client, MockRestServiceServer server) {
    }
}
