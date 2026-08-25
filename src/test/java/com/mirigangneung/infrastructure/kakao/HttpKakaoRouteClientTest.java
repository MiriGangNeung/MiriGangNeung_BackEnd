package com.mirigangneung.infrastructure.kakao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirigangneung.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpKakaoRouteClientTest {
    private static final String RESPONSE = """
            {
              "routes": [{
                "result_code": 0,
                "summary": {"distance": 1234, "duration": 987},
                "sections": [{
                  "roads": [{"vertexes": [128.948, 37.772, 128.949, 37.773]}]
                }]
              }]
            }
            """;

    @Test
    void requestsKakaoWalkingRouteWithLongitudeAndLatitude() {
        Fixture fixture = fixture("secret");
        fixture.server.expect(once(), request -> {
            assertThat(request.getMethod()).isEqualTo(GET);
            assertThat(request.getURI().getPath()).isEqualTo("/v2/routing/walk");
            var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            assertThat(query.getFirst("start_x")).isEqualTo("128.948");
            assertThat(query.getFirst("start_y")).isEqualTo("37.772");
            assertThat(query.getFirst("end_x")).isEqualTo("128.949");
            assertThat(query.getFirst("end_y")).isEqualTo("37.773");
        }).andExpect(header("Authorization", "KakaoAK secret"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        KakaoRouteClient.RouteResult result = fixture.client.walking(37.772, 128.948, 37.773, 128.949);

        assertThat(result.distanceMeters()).isEqualTo(1234);
        assertThat(result.durationSeconds()).isEqualTo(987);
        assertThat(result.polyline()).containsExactly(
                List.of(128.948, 37.772), List.of(128.949, 37.773));
        fixture.server.verify();
    }

    @Test
    void returnsUnavailableResultWhenKeyIsMissing() {
        Fixture fixture = fixture("");

        KakaoRouteClient.RouteResult result = fixture.client.walking(37.772, 128.948, 37.773, 128.949);

        assertThat(result.distanceMeters()).isZero();
        assertThat(result.durationSeconds()).isZero();
        assertThat(result.polyline()).isEmpty();
        fixture.server.verify();
    }

    @Test
    void mapsUpstreamFailureToApiError() {
        Fixture fixture = fixture("secret");
        fixture.server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/v2/routing/walk")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        assertThatThrownBy(() -> fixture.client.walking(37.772, 128.948, 37.773, 128.949))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("KAKAO_API_ERROR"));
        fixture.server.verify();
    }

    private Fixture fixture(String key) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoRouteClient client = new HttpKakaoRouteClient(
                new KakaoRouteProperties("https://example.test", key),
                builder.build(),
                new ObjectMapper());
        return new Fixture(client, server);
    }

    private record Fixture(KakaoRouteClient client, MockRestServiceServer server) {
    }
}
