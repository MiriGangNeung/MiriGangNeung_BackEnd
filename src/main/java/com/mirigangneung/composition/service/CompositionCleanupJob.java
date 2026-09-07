package com.mirigangneung.composition.service;

import com.mirigangneung.composition.repository.CompositionJobRepository;
import com.mirigangneung.infrastructure.storage.TemporaryImageStorage;
import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CompositionCleanupJob {
    private final CompositionJobRepository jobs;
    private final TemporaryImageStorage storage;

    public CompositionCleanupJob(CompositionJobRepository jobs, TemporaryImageStorage storage) {
        this.jobs = jobs;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${IMAGE_CLEANUP_DELAY_MS:3600000}")
    public void cleanup() {
        jobs.findByExpiresAtBefore(OffsetDateTime.now()).forEach(job -> {
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
