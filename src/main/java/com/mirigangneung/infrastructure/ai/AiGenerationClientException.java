package com.mirigangneung.infrastructure.ai;

public class AiGenerationClientException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final boolean retryable;

    public AiGenerationClientException(String code, int httpStatus, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
