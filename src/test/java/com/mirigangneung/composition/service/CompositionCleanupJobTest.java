package com.mirigangneung.composition.service;

import com.mirigangneung.composition.domain.CompositionJob;
import com.mirigangneung.composition.domain.CompositionStatus;
import com.mirigangneung.composition.repository.CompositionJobRepository;
import com.mirigangneung.infrastructure.storage.TemporaryImageStorage;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositionCleanupJobTest {
    @Test
    void deletesOnlyExpiredTerminalJobs() throws Exception {
        CompositionJobRepository jobs = mock(CompositionJobRepository.class);
        TemporaryImageStorage storage = mock(TemporaryImageStorage.class);
        CompositionJob failed = new CompositionJob(
                "place-id", "input.jpg", "image/jpeg", "4:5", null,
                OffsetDateTime.now().minusMinutes(1));
        failed.fail("FAILED", "failed", false);
        when(jobs.findByExpiresAtBeforeAndStatusIn(any(), any())).thenReturn(List.of(failed));

        new CompositionCleanupJob(jobs, storage).cleanup();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<CompositionStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(jobs).findByExpiresAtBeforeAndStatusIn(any(), statuses.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                CompositionStatus.DONE, CompositionStatus.FAILED);
        verify(storage).delete("input.jpg");
        verify(jobs).delete(failed);
    }
}
