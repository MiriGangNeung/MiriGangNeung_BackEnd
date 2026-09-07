package com.mirigangneung.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirigangneung.infrastructure.ai.AiGenerationClient.AiGenerationRequest;
import com.mirigangneung.infrastructure.ai.AiGenerationClient.ImagePayload;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAiGenerationClientTest {
    @Test
    void createsGenerationWithMultipartImagesAndMetadata() {
        Fixture fixture = fixture("secret");
        fixture.server.expect(once(), request -> {
                    assertThat(request.getMethod().name()).isEqualTo("POST");
                    assertThat(request.getURI().getPath()).isEqualTo("/v1/generations");
                    assertThat(request.getHeaders().getContentType().toString())
                            .startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
                    String body = ((MockClientHttpRequest) request)
                            .getBodyAsString(StandardCharsets.ISO_8859_1);
                    assertThat(body).contains("name=\"photo\"", "filename=\"photo.jpg\"");
                    assertThat(body).contains("Content-Type: image/jpeg");
                    assertThat(body).contains("name=\"background\"", "filename=\"background.png\"");
                    assertThat(body).contains("name=\"onePickPlaceId\"", "place-id");
                    assertThat(body).contains("name=\"backgroundImageUrl\"", "https://img.test/bg.png");
                    assertThat(body).contains("name=\"idempotencyKey\"", "job:0");
                })
                .andExpect(header("X-API-Key", "secret"))
                .andRespond(withSuccess(queuedResponse(), MediaType.APPLICATION_JSON));

        var response = fixture.client.create(request());

        assertThat(response.providerJobId()).isEqualTo("provider-1");
        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.provider()).isEqualTo("mock");
        fixture.server.verify();
    }

    @Test
    void parsesDoneStatusWarningsAndDownloadsImage() {
        Fixture fixture = fixture("");
        fixture.server.expect(once(), request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v1/generations/provider-1");
                })
                .andRespond(withSuccess(doneResponse(), MediaType.APPLICATION_JSON));
        fixture.server.expect(once(), request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v1/generations/provider-1/result");
                })
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_PNG));

        var status = fixture.client.getStatus("provider-1");
        var image = fixture.client.downloadResult("provider-1");

        assertThat(status.status()).isEqualTo("DONE");
        assertThat(status.imageReference()).endsWith("/result");
        assertThat(status.warnings()).singleElement().satisfies(warning ->
                assertThat(warning.code()).isEqualTo("FACE_NOT_PRESERVED"));
        assertThat(image.bytes()).containsExactly(1, 2, 3);
        assertThat(image.contentType()).isEqualTo("image/png");
        fixture.server.verify();
    }

    @Test
    void mapsAgentErrorEnvelopeWithoutLeakingRawResponse() {
        Fixture fixture = fixture("secret");
        fixture.server.expect(once(), method(org.springframework.http.HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"code":"IMAGE_TOO_BLURRY","message":"사진이 흐립니다.","retryable":false}}
                                """));

        assertThatThrownBy(() -> fixture.client.create(request()))
                .isInstanceOfSatisfying(AiGenerationClientException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("IMAGE_TOO_BLURRY");
                    assertThat(exception.getHttpStatus()).isEqualTo(422);
                    assertThat(exception.isRetryable()).isFalse();
                    assertThat(exception.getMessage()).isEqualTo("사진이 흐립니다.");
                });
        fixture.server.verify();
    }

    private Fixture fixture(String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiGenerationProperties properties = new AiGenerationProperties(
                "https://agent.test", apiKey, Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(2));
        return new Fixture(new HttpAiGenerationClient(properties, builder.build(), new ObjectMapper()), server);
    }

    private AiGenerationRequest request() {
        return new AiGenerationRequest(
                new ImagePayload(new byte[]{1}, "image/jpeg", "photo.jpg"),
                "place-id",
                "4:5",
                new ImagePayload(new byte[]{2}, "image/png", "background.png"),
                "https://img.test/bg.png",
                "안목해변",
                "강릉시",
                "바다",
                "job:0");
    }

    private String queuedResponse() {
        return """
                {
                  "providerJobId":"provider-1","status":"QUEUED","coarseStatus":"RUNNING",
                  "stage":"요청 접수","progress":0,"result":null,
                  "safety":{"status":"UNKNOWN","reasonCode":null,"warnings":[]},"error":null,
                  "metadata":{"provider":"mock","model":"mock-v1","promptVersion":"v5",
                    "onePickPlaceId":"place-id","createdAt":"2026-09-08T00:00:00Z","styleTags":[]}
                }
                """;
    }

    private String doneResponse() {
        return """
                {
                  "providerJobId":"provider-1","status":"DONE","coarseStatus":"DONE",
                  "stage":"완료","progress":100,
                  "result":{"imageReference":"/v1/generations/provider-1/result","width":1024,"height":1280,"aspectRatio":"4:5"},
                  "safety":{"status":"PASSED","reasonCode":null,"warnings":[
                    {"code":"FACE_NOT_PRESERVED","message":"얼굴이 다를 수 있습니다."}]},"error":null,
                  "metadata":{"provider":"mock","model":"mock-v1","promptVersion":"v5",
                    "onePickPlaceId":"place-id","createdAt":"2026-09-08T00:00:00Z","styleTags":[]}
                }
                """;
    }

    private record Fixture(HttpAiGenerationClient client, MockRestServiceServer server) {
    }
}
