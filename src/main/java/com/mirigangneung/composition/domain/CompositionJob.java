package com.mirigangneung.composition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "composition_jobs")
public class CompositionJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String onePickPlaceId;

    @Enumerated(EnumType.STRING)
    private CompositionStatus status;

    private String stage;
    private Integer progress;
    private String inputStorageKey;
    private String inputContentType;
    private String resultStorageKey;
    private String resultContentType;
    private String providerJobId;
    private String aspectRatio;

    @Column(length = 2000)
    private String backgroundImageUrl;

    /**
     * 비로그인 사용자를 구분하는 익명 브라우저 세션 ID. AI 서비스가 이 값을 키로
     * 시간당 생성 횟수를 센다. 재시도할 때도 같은 값을 보내야 카운터가 갈라지지
     * 않으므로 Job에 저장한다.
     */
    private String sessionId;

    private String provider;
    private String modelVersion;
    private String promptVersion;
    private String safetyStatus;
    private String safetyReasonCode;
    private String warningCode;

    @Column(length = 1000)
    private String warningMessage;

    private String errorCode;

    @Column(length = 1000)
    private String errorMessage;

    private Boolean errorRetryable;
    private int retryCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime expiresAt;

    protected CompositionJob() {
    }

    public CompositionJob(
            String onePickPlaceId,
            String inputStorageKey,
            String inputContentType,
            String aspectRatio,
            String backgroundImageUrl,
            String sessionId,
            OffsetDateTime expiresAt) {
        this.onePickPlaceId = onePickPlaceId;
        this.inputStorageKey = inputStorageKey;
        this.inputContentType = inputContentType;
        this.aspectRatio = aspectRatio;
        this.backgroundImageUrl = backgroundImageUrl;
        this.sessionId = sessionId;
        this.status = CompositionStatus.QUEUED;
        this.stage = "QUEUED";
        this.progress = 0;
        this.createdAt = OffsetDateTime.now();
        this.expiresAt = expiresAt;
    }

    public void applyProviderStatus(
            String providerJobId,
            CompositionStatus providerStatus,
            String stage,
            Integer progress,
            String provider,
            String modelVersion,
            String promptVersion,
            String safetyStatus,
            String safetyReasonCode,
            String warningCode,
            String warningMessage,
            String errorCode,
            String errorMessage,
            Boolean errorRetryable) {
        this.providerJobId = providerJobId;
        status = providerStatus;
        this.stage = hasText(stage) ? stage : providerStatus.name();
        this.progress = progress == null ? this.progress : progress;
        this.provider = provider;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
        this.safetyStatus = safetyStatus;
        this.safetyReasonCode = safetyReasonCode;
        this.warningCode = warningCode;
        this.warningMessage = warningMessage;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorRetryable = errorRetryable;
        if (startedAt == null && providerStatus != CompositionStatus.QUEUED) {
            startedAt = OffsetDateTime.now();
        }
        if (providerStatus == CompositionStatus.FAILED) {
            completedAt = OffsetDateTime.now();
        }
    }

    public void complete(String storageKey, String contentType) {
        resultStorageKey = storageKey;
        resultContentType = contentType;
        status = CompositionStatus.DONE;
        stage = "COMPLETED";
        progress = 100;
        completedAt = OffsetDateTime.now();
    }

    public void fail(String code, String message, boolean retryable) {
        status = CompositionStatus.FAILED;
        stage = "FAILED";
        errorCode = code;
        errorMessage = message;
        errorRetryable = retryable;
        completedAt = OffsetDateTime.now();
    }

    public void retry() {
        status = CompositionStatus.QUEUED;
        stage = "QUEUED";
        progress = 0;
        retryCount++;
        providerJobId = null;
        resultStorageKey = null;
        resultContentType = null;
        errorCode = null;
        errorMessage = null;
        errorRetryable = null;
        provider = null;
        modelVersion = null;
        promptVersion = null;
        safetyStatus = null;
        safetyReasonCode = null;
        warningCode = null;
        warningMessage = null;
        startedAt = null;
        completedAt = null;
    }

    public UUID getId() {
        return id;
    }

    public String getOnePickPlaceId() {
        return onePickPlaceId;
    }

    public CompositionStatus getStatus() {
        return status;
    }

    public String getStage() {
        return stage;
    }

    public Integer getProgress() {
        return progress;
    }

    public String getInputStorageKey() {
        return inputStorageKey;
    }

    public String getInputContentType() {
        return inputContentType;
    }

    public String getResultStorageKey() {
        return resultStorageKey;
    }

    public String getResultContentType() {
        return resultContentType;
    }

    public String getProviderJobId() {
        return providerJobId;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Boolean getErrorRetryable() {
        return errorRetryable;
    }

    public String getSafetyStatus() {
        return safetyStatus;
    }

    public String getSafetyReasonCode() {
        return safetyReasonCode;
    }

    public String getWarningCode() {
        return warningCode;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
