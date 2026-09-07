package com.mirigangneung.composition;

import com.mirigangneung.composition.domain.CompositionStatus;
import com.mirigangneung.composition.dto.CompositionStatusResponse;
import com.mirigangneung.composition.repository.CompositionJobRepository;
import com.mirigangneung.infrastructure.image.PlaceImageStorage;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.place.repository.PlaceImageRepository;
import com.mirigangneung.place.repository.PlaceRepository;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ai.api-key=",
        "ai.poll-delay=100ms",
        "image.cache.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "RUN_AI_MOCK_E2E", matches = "true")
class AiCompositionMockE2ETest {
    private static HttpServer agentServer;
    private static ExecutorService agentExecutor;

    @DynamicPropertySource
    static void agentProperties(DynamicPropertyRegistry registry) {
        try {
            agentServer = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            agentServer.createContext("/", AiCompositionMockE2ETest::handleAgentRequest);
            agentExecutor = Executors.newCachedThreadPool();
            agentServer.setExecutor(agentExecutor);
            agentServer.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Mock Agent 서버를 시작할 수 없습니다.", exception);
        }
        registry.add("ai.base-url", () -> "http://127.0.0.1:" + agentServer.getAddress().getPort());
    }

    @AfterAll
    static void stopAgentServer() {
        if (agentServer != null) {
            agentServer.stop(0);
        }
        if (agentExecutor != null) {
            agentExecutor.shutdownNow();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private CompositionJobRepository jobs;

    @Autowired
    private PlaceRepository places;

    @Autowired
    private PlaceImageRepository images;

    @Autowired
    private PlaceImageStorage placeImageStorage;

    @AfterEach
    void cleanDatabase() {
        jobs.deleteAll();
        images.deleteAll();
        places.deleteAll();
    }

    @Test
    void backendCreatesPollsAndDownloadsMockAgentGeneration() throws Exception {
        byte[] backgroundBytes = imageBytes(640, 800, Color.CYAN, "jpg");
        var storedBackground = placeImageStorage.store(
                "https://e2e.test/background.jpg", backgroundBytes, "image/jpeg");
        Place place = places.save(new Place(
                "e2e-place", "E2E 관광지", "강릉시", "nature", "통합 테스트 배경",
                37.0, 128.0, null, "E2E"));
        images.save(new PlaceImage(
                place,
                "https://e2e.test/background.jpg",
                "E2E 배경",
                "E2E",
                0,
                "Type1",
                storedBackground.originalStorageKey(),
                storedBackground.thumbnailStorageKey(),
                storedBackground.contentType(),
                storedBackground.originalByteSize(),
                storedBackground.thumbnailByteSize()));

        byte[] photoBytes = imageBytes(480, 800, Color.ORANGE, "jpg");
        RestClient client = RestClient.create("http://127.0.0.1:" + port);
        var multipart = new LinkedMultiValueMap<String, Object>();
        multipart.add("photo", imagePart(photoBytes, "image/jpeg", "person.jpg"));
        multipart.add("onePickId", place.getId().toString());
        multipart.add("aspectRatio", "4:5");
        multipart.add("backgroundImageUrl", "https://e2e.test/background.jpg");
        CompositionStatusResponse created = client.post()
                .uri("/api/v1/compositions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart)
                .retrieve()
                .body(CompositionStatusResponse.class);
        assertThat(created).isNotNull();

        var current = created;
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!isTerminal(current.status()) && System.nanoTime() < deadline) {
            Thread.sleep(100);
            current = client.get()
                    .uri("/api/v1/compositions/{jobId}", created.jobId())
                    .retrieve()
                    .body(CompositionStatusResponse.class);
        }

        assertThat(current.status())
                .withFailMessage("AI composition ended with status=%s, error=%s", current.status(), current.error())
                .isEqualTo(CompositionStatus.DONE.name());
        assertThat(current.resultAvailable()).isTrue();
        var download = client.get()
                .uri(current.downloadUrl())
                .retrieve()
                .toEntity(byte[].class);
        assertThat(download.getBody()).isNotEmpty();
        assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
    }

    private static boolean isTerminal(String status) {
        return CompositionStatus.DONE.name().equals(status) || CompositionStatus.FAILED.name().equals(status);
    }

    private static byte[] imageBytes(int width, int height, Color color, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private static void handleAgentRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("POST".equals(exchange.getRequestMethod()) && "/v1/generations".equals(path)) {
            sendJson(exchange, queuedResponse(), 202);
            return;
        }
        if ("GET".equals(exchange.getRequestMethod())
                && "/v1/generations/provider-1".equals(path)) {
            sendJson(exchange, doneResponse(), 200);
            return;
        }
        if ("GET".equals(exchange.getRequestMethod())
                && "/v1/generations/provider-1/result".equals(path)) {
            byte[] image = Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, image.length);
            try (var output = exchange.getResponseBody()) {
                output.write(image);
            }
            return;
        }
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
    }

    private static void sendJson(HttpExchange exchange, String body, int status) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String queuedResponse() {
        return """
                {"providerJobId":"provider-1","status":"QUEUED","stage":"요청 접수","progress":0,
                 "safety":{"status":"UNKNOWN","reasonCode":null,"warnings":[]},"error":null,
                 "metadata":{"provider":"mock","model":"mock-v1","promptVersion":"v5"}}
                """;
    }

    private static String doneResponse() {
        return """
                {"providerJobId":"provider-1","status":"DONE","stage":"완료","progress":100,
                 "result":{"imageReference":"/v1/generations/provider-1/result","width":1,"height":1,"aspectRatio":"4:5"},
                 "safety":{"status":"PASSED","reasonCode":null,"warnings":[]},"error":null,
                 "metadata":{"provider":"mock","model":"mock-v1","promptVersion":"v5"}}
                """;
    }

    private static HttpEntity<ByteArrayResource> imagePart(byte[] bytes, String contentType, String filename) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        return new HttpEntity<>(resource, headers);
    }
}
