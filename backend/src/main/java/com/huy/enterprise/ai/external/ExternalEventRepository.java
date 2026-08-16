package com.huy.enterprise.ai.external;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalEventRepository extends JpaRepository<ExternalEvent, UUID> {
    Optional<ExternalEvent> findByExternalSourceAndExternalEventId(String externalSource, String externalEventId);

    boolean existsByExternalSourceAndExternalEventId(String externalSource, String externalEventId);

    List<ExternalEvent> findByEventDateBetweenOrderByEventDateDesc(LocalDate from, LocalDate to);
}
