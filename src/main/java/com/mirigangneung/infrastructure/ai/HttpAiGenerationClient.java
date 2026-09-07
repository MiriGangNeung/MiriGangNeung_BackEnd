package com.mirigangneung.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpAiGenerationClient implements AiGenerationClient {
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final List<String> SUPPORTED_RESULT_CONTENT_TYPES = List.of(
            MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE, "image/webp");

    private final AiGenerationProperties properties;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    @Autowired
    public HttpAiGenerationClient(AiGenerationProperties properties, ObjectMapper objectMapper) {
        this(properties, buildClient(properties), objectMapper);
    }

    HttpAiGenerationClient(AiGenerationProperties properties, RestClient client, ObjectMapper objectMapper) {
        this.properties = properties;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        return properties.configured();
    }

    @Override
    public AiGenerationResponse create(AiGenerationRequest request) {
        requireConfigured();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("photo", part(request.photo()));
        body.add("onePickPlaceId", request.onePickPlaceId());
        body.add("aspectRatio", request.aspectRatio());
        if (request.background() != null) {
            body.add("background", part(request.background()));
        }
        addText(body, "backgroundImageUrl", request.backgroundImageUrl());
        addText(body, "placeName", request.placeName());
        addText(body, "placeRegion", request.placeRegion());
        addText(body, "placeDescription", request.placeDescription());
        addText(body, "idempotencyKey", request.idempotencyKey());

        try {
            String responseBody = withApiKey(client.post()
                    .uri("/v1/generations")
                    .contentType(MediaType.MULTIPART_FORM_DATA))
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseResponse(responseBody);
        } catch (RestClientResponseException exception) {
            throw mapError(exception);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    @Override
    public AiGenerationResponse getStatus(String providerJobId) {
        requireConfigured();
        try {
            String responseBody = withApiKey(client.get()
                    .uri("/v1/generations/{providerJobId}", providerJobId))
                    .retrieve()
                    .body(String.class);
            return parseResponse(responseBody);
        } catch (RestClientResponseException exception) {
            throw mapError(exception);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    @Override
    public DownloadedImage downloadResult(String providerJobId) {
        requireConfigured();
        try {
            var response = withApiKey(client.get()
                    .uri("/v1/generations/{providerJobId}/result", providerJobId))
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] bytes = response.getBody();
            MediaType contentType = response.getHeaders().getContentType();
            String value = contentType == null
                    ? null
                    : contentType.getType() + "/" + contentType.getSubtype();
            if (bytes == null || bytes.length == 0
                    || value == null
                    || !SUPPORTED_RESULT_CONTENT_TYPES.contains(value.toLowerCase())
                    || !matchesImageSignature(bytes, value)) {
                throw invalidResponse("AI 결과 이미지 응답이 올바르지 않습니다.");
            }
            return new DownloadedImage(bytes, value);
        } catch (RestClientResponseException exception) {
            throw mapError(exception);
        } catch (AiGenerationClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    @Override
    public void cancel(String providerJobId) {
        requireConfigured();
        try {
            withApiKey(client.post()
                    .uri("/v1/generations/{providerJobId}/cancel", providerJobId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw mapError(exception);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private AiGenerationResponse parseResponse(String body) {
        try {
            AgentGenerationResponse response = objectMapper.readValue(body, AgentGenerationResponse.class);
            if (!hasText(response.providerJobId()) || !hasText(response.status())) {
                throw invalidResponse("AI 생성 서비스 응답이 올바르지 않습니다.");
            }
            List<SafetyWarning> warnings = response.safety() == null || response.safety().warnings() == null
                    ? List.of()
                    : response.safety().warnings().stream()
                    .map(warning -> new SafetyWarning(warning.code(), warning.message()))
                    .toList();
            GenerationError error = response.error() == null ? null : new GenerationError(
                    response.error().code(), response.error().message(), response.error().retryable());
            return new AiGenerationResponse(
                    response.providerJobId(),
                    response.status(),
                    response.stage(),
                    response.progress(),
                    response.result() == null ? null : response.result().imageReference(),
                    response.safety() == null ? null : response.safety().status(),
                    response.safety() == null ? null : response.safety().reasonCode(),
                    warnings,
                    error,
                    response.metadata() == null ? null : response.metadata().provider(),
                    response.metadata() == null ? null : response.metadata().model(),
                    response.metadata() == null ? null : response.metadata().promptVersion());
        } catch (AiGenerationClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidResponse("AI 생성 서비스 응답이 올바르지 않습니다.");
        }
    }

    private AiGenerationClientException mapError(RestClientResponseException exception) {
        try {
            AgentErrorEnvelope envelope = objectMapper.readValue(
                    exception.getResponseBodyAsString(), AgentErrorEnvelope.class);
            if (envelope.error() != null && hasText(envelope.error().code())) {
                return new AiGenerationClientException(
                        envelope.error().code(),
                        exception.getStatusCode().value(),
                        hasText(envelope.error().message())
                                ? envelope.error().message()
                                : "AI 생성 요청에 실패했습니다.",
                        envelope.error().retryable());
            }
        } catch (Exception ignored) {
            // Fall through to a normalized upstream error.
        }
        return new AiGenerationClientException(
                "AI_PROVIDER_ERROR",
                exception.getStatusCode().value(),
                "AI 생성 서비스 요청에 실패했습니다.",
                exception.getStatusCode().is5xxServerError());
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new AiGenerationClientException(
                    "AI_NOT_CONFIGURED", 503, "AI 생성 서비스 주소가 설정되지 않았습니다.", false);
        }
    }

    private RestClient.RequestBodySpec withApiKey(RestClient.RequestBodySpec request) {
        if (hasText(properties.apiKey())) {
            request.header(API_KEY_HEADER, properties.apiKey());
        }
        return request;
    }

    private RestClient.RequestHeadersSpec<?> withApiKey(RestClient.RequestHeadersSpec<?> request) {
        if (hasText(properties.apiKey())) {
            request.header(API_KEY_HEADER, properties.apiKey());
        }
        return request;
    }

    private static ByteArrayResource resource(ImagePayload image) {
        return new ByteArrayResource(image.bytes()) {
            @Override
            public String getFilename() {
                return image.filename();
            }
        };
    }

    private static HttpEntity<ByteArrayResource> part(ImagePayload image) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(image.contentType()));
        return new HttpEntity<>(resource(image), headers);
    }

    private static void addText(MultiValueMap<String, Object> body, String key, String value) {
        if (hasText(value)) {
            body.add(key, value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean matchesImageSignature(byte[] bytes, String contentType) {
        return switch (contentType.toLowerCase()) {
            case MediaType.IMAGE_PNG_VALUE -> bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4e
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0d
                    && bytes[5] == 0x0a
                    && bytes[6] == 0x1a
                    && bytes[7] == 0x0a;
            case MediaType.IMAGE_JPEG_VALUE -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private static AiGenerationClientException unavailable() {
        return new AiGenerationClientException(
                "AI_PROVIDER_UNAVAILABLE", 502, "AI 생성 서비스에 연결할 수 없습니다.", true);
    }

    private static AiGenerationClientException invalidResponse(String message) {
        return new AiGenerationClientException("AI_INVALID_RESPONSE", 502, message, true);
    }

    private static RestClient buildClient(AiGenerationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (properties.configured()) {
            builder.baseUrl(stripTrailingSlash(properties.baseUrl().trim()));
        }
        return builder.build();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AgentGenerationResponse(
            String providerJobId,
            String status,
            String stage,
            Integer progress,
            AgentResult result,
            AgentSafety safety,
            AgentError error,
            AgentMetadata metadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AgentResult(String imageReference) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AgentSafety(String status, String reasonCode, List<AgentWarning> warnings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AgentWarning(String code, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AgentError(String code, String message, boolean retryable) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AgentMetadata(String provider, String model, String promptVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AgentErrorEnvelope(AgentError error) {
    }
}
