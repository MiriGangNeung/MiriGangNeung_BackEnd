package com.mirigangneung.composition.repository;

import com.mirigangneung.composition.domain.CompositionJob;
import com.mirigangneung.composition.domain.CompositionStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompositionJobRepository extends JpaRepository<CompositionJob, UUID> {
    List<CompositionJob> findByExpiresAtBefore(OffsetDateTime now);

    List<CompositionJob> findByStatusInAndProviderJobIdIsNotNull(Collection<CompositionStatus> statuses);
}
