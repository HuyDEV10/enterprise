package com.huy.enterprise.ai.demand;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandSeriesMappingRepository extends JpaRepository<DemandSeriesMapping, UUID> {
    Optional<DemandSeriesMapping> findFirstByProductIdAndActiveTrue(UUID productId);

    List<DemandSeriesMapping> findByActiveTrue();

    Optional<DemandSeriesMapping> findBySourceDatasetAndSourceItemIdAndSourceLocationId(
            String sourceDataset,
            String sourceItemId,
            String sourceLocationId);
}
