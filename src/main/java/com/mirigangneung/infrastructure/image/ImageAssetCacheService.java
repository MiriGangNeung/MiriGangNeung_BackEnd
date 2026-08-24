package com.mirigangneung.infrastructure.image;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ImageAssetCacheService {
    private static final Logger log = LoggerFactory.getLogger(ImageAssetCacheService.class);
    private static final String USER_AGENT = "MiriGangNeung/1.0";

    private final ImageCacheProperties properties;
    private final PlaceImageStorage storage;
    private final HttpClient httpClient;

    @Autowired
    public ImageAssetCacheService(ImageCacheProperties properties, PlaceImageStorage storage) {
        this(properties, storage, buildClient(properties));
    }

    ImageAssetCacheService(ImageCacheProperties properties, PlaceImageStorage storage, HttpClient httpClient) {
        this.properties = properties;
        this.storage = storage;
        this.httpClient = httpClient;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public Optional<PlaceImageStorage.StoredImage> ensureCached(String sourceUrl) {
        if (!enabled() || sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(sourceUrl.trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return Optional.empty();
            }
            Optional<PlaceImageStorage.StoredImage> existing = storage.find(sourceUrl);
            if (existing.isPresent()) {
                return existing;
            }

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(properties.timeout())
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "image/*")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("Tourism image download failed: host={}, status={}", uri.getHost(), response.statusCode());
                    return Optional.empty();
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                    log.warn("Tourism image response rejected: host={}, contentType={}", uri.getHost(), contentType);
                    return Optional.empty();
                }
                long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                if (declaredLength > properties.maxDownloadBytes()) {
                    log.warn("Tourism image response too large: host={}, bytes={}", uri.getHost(), declaredLength);
                    return Optional.empty();
                }
                byte[] bytes = readAtMost(body, properties.maxDownloadBytes());
                if (bytes == null) {
                    log.warn("Tourism image response too large: host={}, bytes=over-limit", uri.getHost());
                    return Optional.empty();
                }
                return Optional.of(storage.store(sourceUrl, bytes, contentType));
            }
        } catch (IllegalArgumentException | IOException exception) {
            log.warn("Tourism image download could not be cached: host={}, exception={}",
                    safeHost(sourceUrl), exception.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Tourism image download interrupted: host={}", safeHost(sourceUrl));
            return Optional.empty();
        }
    }

    private static HttpClient buildClient(ImageCacheProperties properties) {
        Duration timeout = properties.timeout();
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(timeout)
                .build();
    }

    private static String safeHost(String sourceUrl) {
        try {
            return URI.create(sourceUrl).getHost();
        } catch (IllegalArgumentException exception) {
            return "unknown";
        }
    }

    private static byte[] readAtMost(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                return null;
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
