package com.mirigangneung.place.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ImageUrlValidator {
    private static final String USER_AGENT = "MiriGangNeung/1.0";

    private final HttpClient httpClient;
    private final Duration timeout;

    @Autowired
    public ImageUrlValidator() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(3))
                .build(), Duration.ofSeconds(5));
    }

    ImageUrlValidator(HttpClient httpClient, Duration timeout) {
        this.httpClient = httpClient;
        this.timeout = timeout;
    }

    public boolean isUsable(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(imageUrl.trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            HttpResponse<Void> response = send(uri, "HEAD");
            if (response.statusCode() == 405 || response.statusCode() == 501) {
                response = send(uri, "GET");
            }
            return response.statusCode() >= 200
                    && response.statusCode() < 300
                    && response.headers().firstValue("Content-Type")
                    .map(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith("image/"))
                    .orElse(false);
        } catch (IllegalArgumentException | java.io.IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private HttpResponse<Void> send(URI uri, String method)
            throws java.io.IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", USER_AGENT);
        if ("GET".equals(method)) {
            builder.header("Range", "bytes=0-0").GET();
        } else {
            builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
    }
}
