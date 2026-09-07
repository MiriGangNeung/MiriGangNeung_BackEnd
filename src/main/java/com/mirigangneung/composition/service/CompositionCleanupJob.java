package com.mirigangneung.composition.service;

import com.mirigangneung.composition.repository.CompositionJobRepository;
import com.mirigangneung.composition.domain.CompositionStatus;
import com.mirigangneung.infrastructure.storage.TemporaryImageStorage;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CompositionCleanupJob {
    private static final EnumSet<CompositionStatus> TERMINAL_STATUSES = EnumSet.of(
            CompositionStatus.DONE, CompositionStatus.FAILED);

    private final CompositionJobRepository jobs;
    private final TemporaryImageStorage storage;

    public CompositionCleanupJob(CompositionJobRepository jobs, TemporaryImageStorage storage) {
        this.jobs = jobs;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${IMAGE_CLEANUP_DELAY_MS:3600000}")
    public void cleanup() {
        jobs.findByExpiresAtBeforeAndStatusIn(OffsetDateTime.now(), TERMINAL_STATUSES).forEach(job -> {
            deleteQuietly(job.getInputStorageKey());
            deleteQuietly(job.getResultStorageKey());
            jobs.delete(job);
        });
    }

    private void deleteQuietly(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            storage.delete(storageKey);
        } catch (Exception ignored) {
            // Storage cleanup is best-effort; database TTL metadata is still removed.
        }
    }
}
