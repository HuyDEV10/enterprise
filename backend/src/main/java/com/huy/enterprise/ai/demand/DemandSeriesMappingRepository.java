package com.huy.enterprise.ai.demand;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandSeriesMappingRepository extends JpaRepository<DemandSeriesMapping, UUID> {
    Optional<DemandSeriesMapping> findFirstByProductIdAndActiveTrue(UUID productId);

    List<DemandSeriesMapping> findByActiveTrue();

    @Query("select distinct m.product.id from DemandSeriesMapping m where m.active = true")
    List<UUID> findActiveProductIds();

    Optional<DemandSeriesMapping> findBySourceDatasetAndSourceItemIdAndSourceLocationId(
            String sourceDataset,
            String sourceItemId,
            String sourceLocationId);
}
