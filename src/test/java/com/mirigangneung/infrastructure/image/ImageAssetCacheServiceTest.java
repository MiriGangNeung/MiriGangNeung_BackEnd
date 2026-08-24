package com.mirigangneung.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageAssetCacheServiceTest {

    @TempDir
    Path root;

    private HttpServer server;
    private AtomicInteger imageRequests;
    private ImageAssetCacheService service;

    @BeforeEach
    void setUp() throws Exception {
        imageRequests = new AtomicInteger();
        byte[] jpeg = jpeg();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/image.jpg", exchange -> {
            imageRequests.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, jpeg.length);
            exchange.getResponseBody().write(jpeg);
            exchange.close();
        });
        server.createContext("/not-image", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, 4);
            exchange.getResponseBody().write("nope".getBytes());
            exchange.close();
        });
        server.createContext("/forbidden.jpg", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();

        ImageCacheProperties properties = new ImageCacheProperties(
                true,
                root.toString(),
                "http://localhost:8080/media/images/",
                Duration.ofSeconds(3),
                2_000_000,
                640,
                Duration.ofDays(365));
        service = new ImageAssetCacheService(properties, new LocalPlaceImageStorage(root.toString(), 640));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void downloadsOnceAndReusesTheStoredAsset() {
        String url = url("/image.jpg");

        var first = service.ensureCached(url);
        var second = service.ensureCached(url);

        assertThat(first).isPresent();
        assertThat(second).contains(first.orElseThrow());
        assertThat(imageRequests).hasValue(1);
    }

    @Test
    void rejectsNonImageResponsesAndFailedStatus() {
        assertThat(service.ensureCached(url("/not-image"))).isEmpty();
        assertThat(service.ensureCached(url("/forbidden.jpg"))).isEmpty();
    }

    @Test
    void rejectsUnsupportedUrlScheme() {
        assertThat(service.ensureCached("file:///tmp/image.jpg")).isEmpty();
    }

    private String url(String path) {
        return "http://localhost:" + server.getAddress().getPort() + path;
    }

    private static byte[] jpeg() throws Exception {
        BufferedImage image = new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
