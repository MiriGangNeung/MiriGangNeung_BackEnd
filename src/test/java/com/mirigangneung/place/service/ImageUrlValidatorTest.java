package com.mirigangneung.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImageUrlValidatorTest {

    private HttpServer server;
    private ImageUrlValidator validator;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/image", exchange -> respond(exchange, 200, "image/jpeg"));
        server.createContext("/html", exchange -> respond(exchange, 200, "text/html"));
        server.createContext("/missing", exchange -> respond(exchange, 404, "text/html"));
        server.createContext("/get-fallback", exchange -> {
            if ("HEAD".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, null);
            } else {
                respond(exchange, 206, "image/png");
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        validator = new ImageUrlValidator(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(),
                Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void acceptsOnlyReachableImageResponses() {
        assertThat(validator.isUsable(baseUrl + "/image")).isTrue();
        assertThat(validator.isUsable(baseUrl + "/get-fallback")).isTrue();
        assertThat(validator.isUsable(baseUrl + "/html")).isFalse();
        assertThat(validator.isUsable(baseUrl + "/missing")).isFalse();
        assertThat(validator.isUsable("not-a-url")).isFalse();
    }

    private static void respond(HttpExchange exchange, int status, String contentType) throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
        }
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
}
