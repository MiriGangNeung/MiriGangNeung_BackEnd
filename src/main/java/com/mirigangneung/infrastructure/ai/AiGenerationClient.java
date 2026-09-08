package com.mirigangneung.infrastructure.ai;

import java.util.List;

public interface AiGenerationClient {
    boolean isConfigured();

    AiGenerationResponse create(AiGenerationRequest request);

    AiGenerationResponse getStatus(String providerJobId);

    DownloadedImage downloadResult(String providerJobId);

    void cancel(String providerJobId);

    record ImagePayload(byte[] bytes, String contentType, String filename) {
    }

    record AiGenerationRequest(
            ImagePayload photo,
            String onePickPlaceId,
            String aspectRatio,
            ImagePayload background,
            String backgroundImageUrl,
            String placeName,
            String placeRegion,
            String placeDescription,
            String idempotencyKey) {
    }

    record AiGenerationResponse(
            String providerJobId,
            String status,
            String stage,
            Integer progress,
            String imageReference,
            String safetyStatus,
            String reasonCode,
            List<SafetyWarning> warnings,
            GenerationError error,
            String provider,
            String modelVersion,
            String promptVersion) {
        public AiGenerationResponse {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    record SafetyWarning(String code, String message) {
    }

    record GenerationError(String code, String message, boolean retryable) {
    }

    record DownloadedImage(byte[] bytes, String contentType) {
    }
}
