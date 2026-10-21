package com.huy.enterprise.ai.demand;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandSeriesMappingRepository extends JpaRepository<DemandSeriesMapping, UUID> {
    Optional<DemandSeriesMapping> findByProductIdAndActiveTrue(UUID productId);

    @Modifying(flushAutomatically = true)
    @Query("update DemandSeriesMapping m set m.active = false, m.updatedAt = CURRENT_TIMESTAMP, m.version = m.version + 1 "
            + "where m.product.id = :productId and m.id <> :exceptId and m.active = true")
    int deactivateOtherMappings(UUID productId, UUID exceptId);

    List<DemandSeriesMapping> findByActiveTrue();

    @Query("select distinct m.product.id from DemandSeriesMapping m where m.active = true")
    List<UUID> findActiveProductIds();

    Optional<DemandSeriesMapping> findBySourceDatasetAndSourceItemIdAndSourceLocationId(
            String sourceDataset,
            String sourceItemId,
            String sourceLocationId);
}
