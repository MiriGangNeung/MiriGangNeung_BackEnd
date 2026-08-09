package com.mirigangneung.infrastructure.tourapi;

import com.mirigangneung.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KoreanTourAwardPhotoClientTest {
    private static final String BASE_URL = "https://example.test/B551011/PhokoAwrdService";
    private static final String SUCCESS_LIST = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[
              {"contentId":"award-1","koTitle":"강릉의 밤","lDongRegnCd":"51","koFilmst":"강원특별자치도 강릉시 경포대","koWnprzDiz":"디지털카메라 부문 [금상]","koKeyWord":"관광공모전, 강릉, 야경","orgImage":"https://img.test/award-original.jpg","thumbImage":"https://img.test/award-thumb.jpg","koCmanNm":"홍길동","cpyrhtDivCd":"Type1"},
              {"contentId":"award-2","koTitle":"이미지 없음","lDongRegnCd":"51","koFilmst":"강릉시","koWnprzDiz":"입선","koKeyWord":"강릉","orgImage":"","thumbImage":"  ","koCmanNm":"촬영자","cpyrhtDivCd":"Type1"}
            ]}}}}
            """;

    @Test
    void searchesPublishedGangwonAwardPhotosAndMapsContestFields() {
        Fixture fixture = fixture("secret%2Bkey");
        fixture.server().expect(once(), request -> assertRequest(request,
                        "/B551011/PhokoAwrdService/phokoAwrdSyncList",
                        Map.of(
                                "serviceKey", "secret+key",
                                "pageNo", "1",
                                "numOfRows", "100",
                                "lDongRegnCd", "51",
                                "showflag", "1",
                                "arrange", "C")))
                .andRespond(withSuccess(SUCCESS_LIST, MediaType.APPLICATION_JSON));

        List<AwardPhotoApiClient.AwardPhoto> result = fixture.client().search("51", 0, 100);

        assertThat(result).singleElement().satisfies(photo -> {
            assertThat(photo.contentId()).isEqualTo("award-1");
            assertThat(photo.title()).isEqualTo("강릉의 밤");
            assertThat(photo.location()).isEqualTo("강원특별자치도 강릉시 경포대");
            assertThat(photo.award()).isEqualTo("디지털카메라 부문 [금상]");
            assertThat(photo.keywords()).containsExactly("관광공모전", "강릉", "야경");
            assertThat(photo.originalImageUrl()).isEqualTo("https://img.test/award-original.jpg");
            assertThat(photo.thumbnailUrl()).isEqualTo("https://img.test/award-thumb.jpg");
            assertThat(photo.photographer()).isEqualTo("홍길동");
            assertThat(photo.copyrightCode()).isEqualTo("Type1");
        });
        fixture.server().verify();
    }

    @Test
    void doesNotCallRemoteWhenServiceKeyIsBlank() {
        Fixture fixture = fixture("");

        assertThat(fixture.client().search("51", 0, 100)).isEmpty();

        fixture.server().verify();
    }

    @Test
    void rejectsUpstreamErrorInsteadOfReturningEmpty() {
        Fixture fixture = fixture("secret");
        String errorResponse = """
                {"response":{"header":{"resultCode":"113","resultMsg":"키 오류"},"body":{}}}
                """;
        fixture.server().expect(once(), request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/B551011/PhokoAwrdService/phokoAwrdSyncList"))
                .andRespond(withSuccess(errorResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().search("51", 0, 100))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("TOUR_API_ERROR"));
        fixture.server().verify();
    }

    private Fixture fixture(String key) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreanTourAwardPhotoClient client = new KoreanTourAwardPhotoClient(
                new AwardPhotoProperties(BASE_URL, key, Duration.ofSeconds(2)),
                builder.build(),
                new TourApiResponseParser());
        return new Fixture(client, server);
    }

    private record Fixture(KoreanTourAwardPhotoClient client, MockRestServiceServer server) {
    }

    private static void assertRequest(org.springframework.http.client.ClientHttpRequest request,
                                      String expectedPath,
                                      Map<String, String> expectedParams) {
        URI uri = request.getURI();
        assertThat(uri.getPath()).isEqualTo(expectedPath);
        org.springframework.util.MultiValueMap<String, String> params =
                org.springframework.web.util.UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        expectedParams.forEach((name, value) -> assertThat(UriUtils.decode(params.getFirst(name), StandardCharsets.UTF_8))
                .as(name).isEqualTo(value));
        assertThat(params.getFirst("MobileOS")).isEqualTo("ETC");
        assertThat(params.getFirst("MobileApp")).isEqualTo("MiriGangNeung");
        assertThat(params.getFirst("_type")).isEqualTo("json");
    }
}
