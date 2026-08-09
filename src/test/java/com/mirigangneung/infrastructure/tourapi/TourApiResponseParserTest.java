package com.mirigangneung.infrastructure.tourapi;

import com.mirigangneung.common.error.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TourApiResponseParserTest {
    private final TourApiResponseParser parser = new TourApiResponseParser();

    @Test
    void parsesJsonArrayItems() {
        String body = """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {"items": {"item": [
                      {"contentid": "100", "title": "경포대"},
                      {"contentid": "200", "title": "안목해변"}
                    ]}}
                  }
                }
                """;

        List<com.fasterxml.jackson.databind.JsonNode> items = parser.parseItems(body);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("contentid").asText()).isEqualTo("100");
        assertThat(items.get(1).path("title").asText()).isEqualTo("안목해변");
    }

    @Test
    void parsesJsonSingleItem() {
        String body = """
                {
                  "response": {
                    "header": {"resultCode": "0000"},
                    "body": {"items": {"item": {"contentid": "100", "title": "경포대"}}}
                  }
                }
                """;

        assertThat(parser.parseItems(body)).singleElement()
                .extracting(node -> node.path("title").asText())
                .isEqualTo("경포대");
    }

    @Test
    void parsesXmlItems() {
        String body = """
                <response>
                  <header><resultCode>0000</resultCode><resultMsg>OK</resultMsg></header>
                  <body><items><item><contentid>100</contentid><title>경포대</title></item></items></body>
                </response>
                """;

        assertThat(parser.parseItems(body)).singleElement()
                .extracting(node -> node.path("contentid").asText())
                .isEqualTo("100");
    }

    @Test
    void returnsEmptyForSuccessfulEmptyItems() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":""}}}
                """;

        assertThat(parser.parseItems(body)).isEmpty();
    }

    @Test
    void rejectsUpstreamErrorResponse() {
        String body = """
                {"response":{"header":{"resultCode":"113","resultMsg":"서비스키 오류"},"body":{}}}
                """;

        assertThatThrownBy(() -> parser.parseItems(body))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("TOUR_API_ERROR"));
    }

    @Test
    void rejectsMissingEnvelopeAndMalformedBody() {
        assertThatThrownBy(() -> parser.parseItems("{}"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parser.parseItems("not-json"))
                .isInstanceOf(ApiException.class);
    }
}
