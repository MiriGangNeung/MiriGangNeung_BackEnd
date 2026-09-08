package com.mirigangneung.composition.dto;

import com.mirigangneung.composition.domain.CompositionJob;
import java.util.List;

public record CompositionStatusResponse(
        String jobId,
        String status,
        Integer progress,
        String stage,
        boolean resultAvailable,
        String downloadUrl,
        Object place,
        CompositionErrorResponse error,
        CompositionSafetyResponse safety) {

    public static CompositionStatusResponse from(CompositionJob job, boolean available) {
        CompositionErrorResponse error = job.getErrorCode() == null ? null : new CompositionErrorResponse(
                job.getErrorCode(), job.getErrorMessage(), Boolean.TRUE.equals(job.getErrorRetryable()));
        List<CompositionWarningResponse> warnings = job.getWarningCode() == null
                ? List.of()
                : List.of(new CompositionWarningResponse(job.getWarningCode(), job.getWarningMessage()));
        CompositionSafetyResponse safety = job.getSafetyStatus() == null && warnings.isEmpty()
                ? null
                : new CompositionSafetyResponse(job.getSafetyStatus(), job.getSafetyReasonCode(), warnings);
        return new CompositionStatusResponse(
                job.getId().toString(),
                job.getStatus().name(),
                job.getProgress(),
                job.getStage(),
                available,
                available ? "/api/v1/compositions/" + job.getId() + "/download" : null,
                null,
                error,
                safety);
    }

    public record CompositionErrorResponse(String code, String message, boolean retryable) {
    }

    public record CompositionSafetyResponse(
            String status,
            String reasonCode,
            List<CompositionWarningResponse> warnings) {
    }

    public record CompositionWarningResponse(String code, String message) {
    }
}
