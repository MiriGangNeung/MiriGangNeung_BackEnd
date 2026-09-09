package com.mirigangneung.composition.service;

import com.mirigangneung.composition.domain.CompositionJob;
import com.mirigangneung.composition.domain.CompositionStatus;
import com.mirigangneung.composition.repository.CompositionJobRepository;
import com.mirigangneung.infrastructure.ai.AiGenerationClient;
import com.mirigangneung.infrastructure.ai.AiGenerationClient.AiGenerationResponse;
import com.mirigangneung.infrastructure.ai.AiGenerationClient.DownloadedImage;
import com.mirigangneung.infrastructure.ai.AiGenerationClient.GenerationError;
import com.mirigangneung.infrastructure.ai.AiGenerationClient.ImagePayload;
import com.mirigangneung.infrastructure.ai.AiGenerationClientException;
import com.mirigangneung.infrastructure.storage.TemporaryImageStorage;
import com.mirigangneung.place.domain.Place;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositionServiceTest {
    private CompositionJobRepository jobs;
    private TemporaryImageStorage storage;
    private AiGenerationClient ai;
    private CompositionBackgroundResolver backgrounds;
    private CompositionService service;
    private Place place;
    private CompositionBackgroundResolver.ResolvedBackground background;

    @BeforeEach
    void setUp() throws Exception {
        jobs = mock(CompositionJobRepository.class);
        storage = mock(TemporaryImageStorage.class);
        ai = mock(AiGenerationClient.class);
        backgrounds = mock(CompositionBackgroundResolver.class);
        service = new CompositionService(jobs, storage, ai, backgrounds, 86_400);
        place = new Place("100", "안목해변", "강릉시", "nature", "바다",
                37.0, 128.0, null, "KTO");
        ReflectionTestUtils.setField(place, "id", UUID.randomUUID());
        background = new CompositionBackgroundResolver.ResolvedBackground(
                place,
                "https://img.test/background.jpg",
                new ImagePayload(new byte[]{4, 5, 6}, "image/jpeg", "background.jpg"));

        when(ai.isConfigured()).thenReturn(true);
        when(backgrounds.resolve(anyString(), any())).thenReturn(background);
        when(storage.save(any(), anyString(), anyLong(), any())).thenReturn("input.jpg", "result.png");
        when(storage.open("input.jpg")).thenAnswer(ignored -> new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(jobs.save(any(CompositionJob.class))).thenAnswer(invocation -> {
            CompositionJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
            }
            return job;
        });
    }

    @Test
    void createsProviderGenerationAndStoresProviderJobId() {
        when(ai.create(any())).thenReturn(response("QUEUED", null));

        var result = service.create(photo(), place.getId().toString(), null, null, "session-1");

        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(result.resultAvailable()).isFalse();
        verify(ai).create(org.mockito.ArgumentMatchers.argThat(request ->
                request.onePickPlaceId().equals(place.getId().toString())
                        && request.aspectRatio().equals("4:5")
                        && request.photo().bytes().length == 3
                        && request.background().bytes().length == 3
                        && request.backgroundImageUrl().equals("https://img.test/background.jpg")));
    }

    @Test
    void pollingDoneDownloadsAndStoresResult() {
        when(ai.create(any())).thenReturn(response("QUEUED", null));
        var created = service.create(photo(), place.getId().toString(), "1:1", null, "session-1");
        CompositionJob job = capturedSavedJob();
        when(jobs.findByStatusInAndProviderJobIdIsNotNull(any())).thenReturn(List.of(job));
        when(ai.getStatus("provider-1")).thenReturn(response("DONE", null));
        when(ai.downloadResult("provider-1")).thenReturn(new DownloadedImage(new byte[]{9, 8}, "image/png"));
        when(storage.exists("result.png")).thenReturn(true);

        service.pollPendingJobs();
        var result = service.get(created.jobId());

        assertThat(result.status()).isEqualTo("DONE");
        assertThat(result.progress()).isEqualTo(100);
        assertThat(result.resultAvailable()).isTrue();
        assertThat(result.downloadUrl()).endsWith("/download");
    }

    @Test
    void resultDownloadFailureNeverLeavesLocalJobDone() {
        when(ai.create(any())).thenReturn(response("provider-1", "QUEUED", null));
        var created = service.create(photo(), place.getId().toString(), "1:1", null, "session-1");
        CompositionJob job = capturedSavedJob();
        when(jobs.findByStatusInAndProviderJobIdIsNotNull(any())).thenReturn(List.of(job));
        when(ai.getStatus("provider-1")).thenReturn(response("provider-1", "DONE", null));
        when(ai.downloadResult("provider-1")).thenThrow(new AiGenerationClientException(
                "AI_PROVIDER_UNAVAILABLE", 502, "결과 다운로드 실패", true));

        service.pollPendingJobs();
        var result = service.get(created.jobId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.resultAvailable()).isFalse();
        assertThat(result.downloadUrl()).isNull();
        assertThat(result.error().retryable()).isTrue();
    }

    @Test
    void failedProviderJobCanRetryByCreatingANewProviderJob() {
        when(ai.create(any()))
                .thenReturn(response("provider-1", "FAILED", new GenerationError("PROVIDER_TIMEOUT", "시간 초과", true)))
                .thenReturn(response("provider-2", "QUEUED", null));

        var failed = service.create(photo(), place.getId().toString(), "4:5", null, "session-1");
        CompositionJob job = capturedSavedJob();
        when(jobs.findById(job.getId())).thenReturn(java.util.Optional.of(job));
        when(storage.exists("input.jpg")).thenReturn(true);

        var retried = service.retry(failed.jobId());

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.error().retryable()).isTrue();
        assertThat(retried.status()).isEqualTo("QUEUED");
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getProviderJobId()).isEqualTo("provider-2");
        verify(ai, org.mockito.Mockito.times(2)).create(any());
    }

    @Test
    void staleProviderResponseDoesNotOverwriteRetriedGeneration() {
        when(ai.create(any()))
                .thenReturn(response("provider-1", "FAILED", new GenerationError("PROVIDER_TIMEOUT", "시간 초과", true)))
                .thenReturn(response("provider-2", "QUEUED", null));
        var failed = service.create(photo(), place.getId().toString(), "4:5", null, "session-1");
        CompositionJob job = capturedSavedJob();
        when(storage.exists("input.jpg")).thenReturn(true);
        service.retry(failed.jobId());
        when(jobs.findByStatusInAndProviderJobIdIsNotNull(any())).thenReturn(List.of(job));
        when(ai.getStatus("provider-2")).thenReturn(response("provider-1", "DONE", null));

        service.pollPendingJobs();

        assertThat(job.getProviderJobId()).isEqualTo("provider-2");
        assertThat(job.getStatus()).isEqualTo(CompositionStatus.QUEUED);
        verify(ai, org.mockito.Mockito.never()).downloadResult(anyString());
    }

    private CompositionJob capturedSavedJob() {
        var captor = org.mockito.ArgumentCaptor.forClass(CompositionJob.class);
        verify(jobs, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        CompositionJob job = captor.getAllValues().get(captor.getAllValues().size() - 1);
        when(jobs.findById(job.getId())).thenReturn(java.util.Optional.of(job));
        return job;
    }

    private MockMultipartFile photo() {
        return new MockMultipartFile("photo", "person.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private AiGenerationResponse response(String status, GenerationError error) {
        return response("provider-1", status, error);
    }

    private AiGenerationResponse response(String providerJobId, String status, GenerationError error) {
        return new AiGenerationResponse(
                providerJobId,
                status,
                status.equals("DONE") ? "완료" : status,
                status.equals("DONE") ? 100 : 0,
                status.equals("DONE") ? "/v1/generations/" + providerJobId + "/result" : null,
                status.equals("DONE") ? "PASSED" : "UNKNOWN",
                null,
                List.of(),
                error,
                "mock",
                "mock-v1",
                "v5");
    }
}
