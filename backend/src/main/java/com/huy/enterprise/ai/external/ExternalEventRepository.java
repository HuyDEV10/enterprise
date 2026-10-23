package com.huy.enterprise.ai.external;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalEventRepository extends JpaRepository<ExternalEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from ExternalEvent event where event.id = :id")
    Optional<ExternalEvent> findByIdForUpdate(UUID id);
    Optional<ExternalEvent> findByExternalSourceAndExternalEventId(String externalSource, String externalEventId);

    boolean existsByExternalSourceAndExternalEventId(String externalSource, String externalEventId);

    List<ExternalEvent> findByEventDateBetweenOrderByEventDateDesc(LocalDate from, LocalDate to);
}
