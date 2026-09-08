package com.mirigangneung.composition.service;

import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.composition.domain.CompositionJob;
import com.mirigangneung.composition.domain.CompositionStatus;
import com.mirigangneung.composition.dto.CompositionStatusResponse;
import com.mirigangneung.composition.repository.CompositionJobRepository;
import com.mirigangneung.infrastructure.ai.AiGenerationClient;
import com.mirigangneung.infrastructure.ai.AiGenerationClient.AiGenerationRequest;
import com.mirigangneung.infrastructure.ai.AiGenerationClient.AiGenerationResponse;
import com.mirigangneung.infrastructure.ai.AiGenerationClient.DownloadedImage;
import com.mirigangneung.infrastructure.ai.AiGenerationClient.ImagePayload;
import com.mirigangneung.infrastructure.ai.AiGenerationClientException;
import com.mirigangneung.infrastructure.storage.TemporaryImageStorage;
import com.mirigangneung.place.domain.Place;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CompositionService {
    private static final Logger log = LoggerFactory.getLogger(CompositionService.class);
    private static final List<String> SUPPORTED_CONTENT_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp");
    private static final List<String> SUPPORTED_ASPECT_RATIOS = List.of("1:1", "4:5", "9:16");
    private static final EnumSet<CompositionStatus> ACTIVE_STATUSES = EnumSet.of(
            CompositionStatus.QUEUED,
            CompositionStatus.ANALYZING,
            CompositionStatus.COMPOSITING,
            CompositionStatus.QUALITY_CHECK);

    private final CompositionJobRepository jobs;
    private final TemporaryImageStorage storage;
    private final AiGenerationClient ai;
    private final CompositionBackgroundResolver backgroundResolver;
    private final long imageTtlSeconds;

    public CompositionService(
            CompositionJobRepository jobs,
            TemporaryImageStorage storage,
            AiGenerationClient ai,
            CompositionBackgroundResolver backgroundResolver,
            @Value("${image.ttl:86400}") long imageTtlSeconds) {
        this.jobs = jobs;
        this.storage = storage;
        this.ai = ai;
        this.backgroundResolver = backgroundResolver;
        this.imageTtlSeconds = imageTtlSeconds > 0 ? imageTtlSeconds : 86_400;
    }

    public CompositionStatusResponse create(
            MultipartFile photo,
            String onePickId,
            String aspectRatio,
            String backgroundImageUrl) {
        validatePhoto(photo);
        String normalizedAspectRatio = normalizeAspectRatio(aspectRatio);
        requireAiConfigured();
        CompositionBackgroundResolver.ResolvedBackground background =
                backgroundResolver.resolve(onePickId, backgroundImageUrl);

        OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(imageTtlSeconds);
        String inputStorageKey = saveInput(photo, expiresAt);
        CompositionJob job = jobs.save(new CompositionJob(
                onePickId,
                inputStorageKey,
                photo.getContentType(),
                normalizedAspectRatio,
                background.sourceImageUrl(),
                expiresAt));
        startGeneration(job, background);
        jobs.save(job);
        return response(job);
    }

    @Transactional(readOnly = true)
    public CompositionStatusResponse get(String id) {
        return response(find(id));
    }

    public synchronized CompositionStatusResponse retry(String id) {
        CompositionJob job = find(id);
        if (job.getStatus() != CompositionStatus.FAILED) {
            throw new ApiException(
                    "INVALID_COMPOSITION_STATE", HttpStatus.CONFLICT, "재시도할 수 없는 상태입니다.");
        }
        if (Boolean.FALSE.equals(job.getErrorRetryable())) {
            throw new ApiException(
                    "COMPOSITION_NOT_RETRYABLE", HttpStatus.CONFLICT, "재시도할 수 없는 생성 오류입니다.");
        }
        if (!storage.exists(job.getInputStorageKey())) {
            throw new ApiException(
                    "COMPOSITION_EXPIRED", HttpStatus.GONE, "재시도할 원본 이미지가 만료되었습니다.");
        }
        requireAiConfigured();

        job.retry();
        try {
            CompositionBackgroundResolver.ResolvedBackground background =
                    backgroundResolver.resolve(job.getOnePickPlaceId(), job.getBackgroundImageUrl());
            startGeneration(job, background);
        } catch (ApiException exception) {
            job.fail(
                    exception.getCode(),
                    exception.getMessage(),
                    exception.getStatus().is5xxServerError());
        }
        jobs.save(job);
        return response(job);
    }

    public synchronized void pollPendingJobs() {
        if (!ai.isConfigured()) {
            return;
        }
        for (CompositionJob job : jobs.findByStatusInAndProviderJobIdIsNotNull(ACTIVE_STATUSES)) {
            poll(job);
            jobs.save(job);
        }
    }

    public CompositionDownload download(String id) {
        CompositionJob job = find(id);
        if (job.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ApiException(
                    "COMPOSITION_EXPIRED", HttpStatus.GONE, "생성 결과가 만료되었습니다.");
        }
        if (job.getStatus() != CompositionStatus.DONE
                || job.getResultStorageKey() == null
                || !storage.exists(job.getResultStorageKey())) {
            throw new ApiException(
                    "COMPOSITION_NOT_READY", HttpStatus.CONFLICT, "생성 결과가 아직 준비되지 않았습니다.");
        }
        try {
            return new CompositionDownload(
                    storage.open(job.getResultStorageKey()),
                    contentTypeOrDefault(job.getResultContentType()),
                    "composition-" + job.getId() + extension(job.getResultContentType()));
        } catch (IOException exception) {
            throw new ApiException(
                    "COMPOSITION_NOT_FOUND", HttpStatus.NOT_FOUND, "생성 결과를 찾을 수 없습니다.");
        }
    }

    public CompositionJob find(String id) {
        try {
            return jobs.findById(UUID.fromString(id)).orElseThrow(this::notFound);
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    private void startGeneration(
            CompositionJob job,
            CompositionBackgroundResolver.ResolvedBackground background) {
        try {
            Place place = background.place();
            AiGenerationResponse response = ai.create(new AiGenerationRequest(
                    readInput(job),
                    job.getOnePickPlaceId(),
                    job.getAspectRatio(),
                    background.image(),
                    background.sourceImageUrl(),
                    place.getName(),
                    place.getRegion(),
                    place.getDescription(),
                    job.getId() + ":" + job.getRetryCount()));
            applyProviderResponse(job, response);
        } catch (AiGenerationClientException exception) {
            job.fail(exception.getCode(), exception.getMessage(), exception.isRetryable());
        } catch (IOException exception) {
            job.fail("INPUT_IMAGE_UNAVAILABLE", "업로드한 이미지를 읽을 수 없습니다.", false);
        }
    }

    private void poll(CompositionJob job) {
        try {
            applyProviderResponse(job, ai.getStatus(job.getProviderJobId()));
        } catch (AiGenerationClientException exception) {
            if (exception.isRetryable()) {
                log.warn("AI generation status polling failed temporarily: jobId={}, code={}",
                        job.getId(), exception.getCode());
                return;
            }
            job.fail(exception.getCode(), exception.getMessage(), false);
        }
    }

    private void applyProviderResponse(CompositionJob job, AiGenerationResponse response) {
        if (job.getProviderJobId() != null
                && !job.getProviderJobId().equals(response.providerJobId())) {
            log.warn("Ignoring stale AI generation response: jobId={}, expectedProviderJobId={}",
                    job.getId(), job.getProviderJobId());
            return;
        }
        CompositionStatus status;
        try {
            status = CompositionStatus.valueOf(response.status().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            job.fail("AI_INVALID_STATUS", "AI 생성 서비스가 알 수 없는 상태를 반환했습니다.", true);
            return;
        }

        if (status == CompositionStatus.DONE) {
            StoredResult storedResult = saveResult(job);
            if (storedResult != null) {
                applyProviderStatus(job, response, status);
                job.complete(storedResult.storageKey(), storedResult.contentType());
            }
            return;
        }
        applyProviderStatus(job, response, status);
        if (status == CompositionStatus.FAILED && response.error() == null) {
            job.fail("AI_GENERATION_FAILED", "AI 이미지 생성에 실패했습니다.", false);
        }
    }

    private void applyProviderStatus(
            CompositionJob job, AiGenerationResponse response, CompositionStatus status) {
        var warning = response.warnings().isEmpty() ? null : response.warnings().get(0);
        var error = response.error();
        job.applyProviderStatus(
                response.providerJobId(),
                status,
                response.stage(),
                response.progress(),
                response.provider(),
                response.modelVersion(),
                response.promptVersion(),
                response.safetyStatus(),
                response.reasonCode(),
                warning == null ? null : warning.code(),
                warning == null ? null : warning.message(),
                error == null ? null : error.code(),
                error == null ? null : error.message(),
                error == null ? null : error.retryable());
    }

    private StoredResult saveResult(CompositionJob job) {
        try {
            DownloadedImage result = ai.downloadResult(job.getProviderJobId());
            String storageKey = storage.save(
                    new java.io.ByteArrayInputStream(result.bytes()),
                    result.contentType(),
                    result.bytes().length,
                    job.getExpiresAt().toInstant());
            return new StoredResult(storageKey, result.contentType());
        } catch (AiGenerationClientException exception) {
            job.fail(exception.getCode(), exception.getMessage(), exception.isRetryable());
        } catch (IOException exception) {
            job.fail("RESULT_STORAGE_ERROR", "생성 결과를 저장할 수 없습니다.", true);
        }
        return null;
    }

    private ImagePayload readInput(CompositionJob job) throws IOException {
        try (InputStream input = storage.open(job.getInputStorageKey())) {
            return new ImagePayload(
                    input.readAllBytes(),
                    contentTypeOrDefault(job.getInputContentType()),
                    "photo" + extension(job.getInputContentType()));
        }
    }

    private String saveInput(MultipartFile photo, OffsetDateTime expiresAt) {
        try {
            return storage.save(
                    photo.getInputStream(),
                    photo.getContentType(),
                    photo.getSize(),
                    expiresAt.toInstant());
        } catch (IOException exception) {
            throw new ApiException(
                    "INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "이미지를 저장할 수 없습니다.");
        }
    }

    private CompositionStatusResponse response(CompositionJob job) {
        boolean available = job.getStatus() == CompositionStatus.DONE
                && job.getResultStorageKey() != null
                && storage.exists(job.getResultStorageKey());
        return CompositionStatusResponse.from(job, available);
    }

    private void validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()
                || !SUPPORTED_CONTENT_TYPES.contains(photo.getContentType())) {
            throw new ApiException(
                    "UNSUPPORTED_IMAGE",
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "지원하지 않는 이미지 형식입니다.");
        }
    }

    private String normalizeAspectRatio(String aspectRatio) {
        String normalized = aspectRatio == null || aspectRatio.isBlank() ? "4:5" : aspectRatio.trim();
        if (!SUPPORTED_ASPECT_RATIOS.contains(normalized)) {
            throw new ApiException(
                    "INVALID_ASPECT_RATIO",
                    HttpStatus.BAD_REQUEST,
                    "aspectRatio는 1:1, 4:5, 9:16 중 하나여야 합니다.");
        }
        return normalized;
    }

    private void requireAiConfigured() {
        if (!ai.isConfigured()) {
            throw new ApiException(
                    "AI_NOT_CONFIGURED",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 생성 서비스 주소가 설정되지 않았습니다.");
        }
    }

    private ApiException notFound() {
        return new ApiException(
                "COMPOSITION_NOT_FOUND", HttpStatus.NOT_FOUND, "생성 작업을 찾을 수 없습니다.");
    }

    private static String contentTypeOrDefault(String contentType) {
        return contentType == null || contentType.isBlank() ? "image/png" : contentType;
    }

    private static String extension(String contentType) {
        if ("image/jpeg".equalsIgnoreCase(contentType)) {
            return ".jpg";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return ".webp";
        }
        return ".png";
    }

    public record CompositionDownload(InputStream input, String contentType, String filename) {
    }

    private record StoredResult(String storageKey, String contentType) {
    }
}
