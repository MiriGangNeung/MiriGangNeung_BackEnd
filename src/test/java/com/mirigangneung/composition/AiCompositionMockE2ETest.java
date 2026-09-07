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
import java.time.Duration;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ai.base-url=http://127.0.0.1:8100",
        "ai.api-key=",
        "ai.poll-delay=100ms",
        "image.cache.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "RUN_AI_MOCK_E2E", matches = "true")
class AiCompositionMockE2ETest {
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

        assertThat(current.status()).isEqualTo(CompositionStatus.DONE.name());
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
