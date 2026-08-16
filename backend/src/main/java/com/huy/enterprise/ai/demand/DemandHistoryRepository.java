package com.huy.enterprise.ai.demand;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DemandHistoryRepository extends JpaRepository<DemandHistory, UUID> {
    List<DemandHistory> findByMappingIdOrderByDemandDateAsc(UUID mappingId);

    List<DemandHistory> findByMappingIdAndDemandDateBetweenOrderByDemandDateAsc(
            UUID mappingId,
            LocalDate from,
            LocalDate to);

    long countByMappingId(UUID mappingId);

    void deleteByMappingId(UUID mappingId);
}
