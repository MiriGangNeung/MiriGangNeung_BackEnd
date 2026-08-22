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

class KoreanTourApiClientTest {
    private static final String BASE_URL = "https://example.test/B551011/KorService2";
    private static final String SUCCESS_LIST = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[
              {"contentid":"100","title":"경포대","addr1":"강릉시","contenttypeid":"12","overview":"호수 전망","mapy":"37.8","mapx":"128.9","firstimage":"https://img.test/one.jpg","cpyrhtDivCd":"Y","modifiedtime":"20260809120000"}
            ]}}}}
            """;

    @Test
    void doesNotCallRemoteWhenServiceKeyIsBlank() {
        Fixture fixture = fixture("");

        assertThat(fixture.client().search(null, null, 0, 20)).isEmpty();

        fixture.server().verify();
    }

    @Test
    void searchesByAreaWithGangneungParameters() {
        Fixture fixture = fixture("secret%2Bkey");
        fixture.server().expect(once(), request -> assertRequest(request, "/B551011/KorService2/areaBasedList2", Map.of(
                        "serviceKey", "secret+key",
                        "contentTypeId", "12",
                        "lDongRegnCd", "51",
                        "lDongSignguCd", "150",
                        "pageNo", "3",
                        "numOfRows", "20",
                        "arrange", "A")))
                .andRespond(withSuccess(SUCCESS_LIST, MediaType.APPLICATION_JSON));

        List<TourApiClient.TourPlace> result = fixture.client().search(null, null, 2, 20);

        assertThat(result).singleElement().satisfies(place -> {
            assertThat(place.contentId()).isEqualTo("100");
            assertThat(place.category()).isEqualTo("nature");
            assertThat(place.latitude()).isEqualTo(37.8);
            assertThat(place.longitude()).isEqualTo(128.9);
            assertThat(place.sourceUpdatedAt()).isNotNull();
            assertThat(place.images()).singleElement().extracting(TourApiClient.TourImage::imageUrl)
                    .isEqualTo("https://img.test/one.jpg");
        });
        fixture.server().verify();
    }

    @Test
    void searchesByKeywordAndMapsCategory() {
        Fixture fixture = fixture("secret");
        fixture.server().expect(once(), request -> assertRequest(request, "/B551011/KorService2/searchKeyword2", Map.of(
                        "keyword", "커피",
                        "contentTypeId", "39",
                        "lDongRegnCd", "51",
                        "lDongSignguCd", "150",
                        "pageNo", "1",
                        "numOfRows", "10",
                        "arrange", "A")))
                .andRespond(withSuccess(SUCCESS_LIST, MediaType.APPLICATION_JSON));

        assertThat(fixture.client().search("커피", "food", 0, 10)).hasSize(1);

        fixture.server().verify();
    }

    @Test
    void findsPlaceAndMergesDetailImagesWithoutDuplicates() {
        Fixture fixture = fixture("secret");
        fixture.server().expect(once(), request -> assertRequest(request, "/B551011/KorService2/detailCommon2", Map.of(
                        "contentId", "100",
                        "defaultYN", "Y",
                        "firstImageYN", "Y",
                        "overviewYN", "Y")))
                .andRespond(withSuccess(SUCCESS_LIST, MediaType.APPLICATION_JSON));
        String imageResponse = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[
                  {"originimgurl":"https://img.test/one.jpg","smallimageurl":"https://img.test/one-small.jpg","imgname":"대표","cpyrhtDivCd":"Y","serialnum":"0"},
                  {"originimgurl":"https://img.test/two.jpg","smallimageurl":"https://img.test/two-small.jpg","imgname":"호수","cpyrhtDivCd":"N","serialnum":"1"}
                ]}}}}
                """;
        fixture.server().expect(once(), request -> assertRequest(request, "/B551011/KorService2/detailImage2", Map.of(
                        "contentId", "100",
                        "imageYN", "Y")))
                .andRespond(withSuccess(imageResponse, MediaType.APPLICATION_JSON));

        TourApiClient.TourPlace result = fixture.client().find("100").orElseThrow();

        assertThat(result.images()).extracting(TourApiClient.TourImage::imageUrl)
                .containsExactly("https://img.test/one.jpg", "https://img.test/two.jpg");
        assertThat(result.images().get(1).copyrightCode()).isEqualTo("N");
        assertThat(result.imageUrls()).containsExactly("https://img.test/one.jpg", "https://img.test/two.jpg");
        fixture.server().verify();
    }

    @Test
    void exposesDetailIntroAsAnExtensionResult() {
        Fixture fixture = fixture("secret");
        String introResponse = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":{
                  "usetime":"09:00~18:00","restdate":"월요일","parking":"가능","infocenter":"033-000-0000"
                }}}}}
                """;
        fixture.server().expect(once(), request -> assertRequest(request, "/B551011/KorService2/detailIntro2", Map.of(
                        "contentId", "100",
                        "contentTypeId", "12")))
                .andRespond(withSuccess(introResponse, MediaType.APPLICATION_JSON));

        TourApiClient.TourPlaceIntro intro = fixture.client().intro("100", "12").orElseThrow();

        assertThat(intro.useTime()).isEqualTo("09:00~18:00");
        assertThat(intro.restDate()).isEqualTo("월요일");
        assertThat(intro.parking()).isEqualTo("가능");
        assertThat(intro.infoCenter()).isEqualTo("033-000-0000");
        fixture.server().verify();
    }

    @Test
    void rejectsUpstreamErrorInsteadOfReturningEmpty() {
        Fixture fixture = fixture("secret");
        String errorResponse = """
                {"response":{"header":{"resultCode":"113","resultMsg":"키 오류"},"body":{}}}
                """;
        fixture.server().expect(once(), request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/B551011/KorService2/areaBasedList2"))
                .andRespond(withSuccess(errorResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().search(null, null, 0, 20))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("TOUR_API_ERROR"));
        fixture.server().verify();
    }

    @Test
    void doesNotPretendDetailInfoIsRelatedTourism() {
        Fixture fixture = fixture("secret");

        assertThat(fixture.client().related("100")).isEmpty();

        fixture.server().verify();
    }

    private Fixture fixture(String key) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreanTourApiClient client = new KoreanTourApiClient(
                new TourApiProperties(BASE_URL, key, Duration.ofSeconds(2)),
                builder.build(),
                new TourApiResponseParser());
        return new Fixture(client, server);
    }

    private record Fixture(KoreanTourApiClient client, MockRestServiceServer server) {
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
        if ("secret+key".equals(expectedParams.get("serviceKey"))) {
            assertThat(uri.getRawQuery()).contains("serviceKey=secret%2Bkey");
        }
        assertThat(params.getFirst("MobileOS")).isEqualTo("ETC");
        assertThat(params.getFirst("MobileApp")).isEqualTo("MiriGangNeung");
        assertThat(params.getFirst("_type")).isEqualTo("json");
    }
}
