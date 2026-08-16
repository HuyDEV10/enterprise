package com.huy.enterprise.ai.external;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalEventImpactRepository extends JpaRepository<ExternalEventImpact, UUID> {
    List<ExternalEventImpact> findByExternalEventId(UUID externalEventId);

    List<ExternalEventImpact> findBySupplierId(UUID supplierId);

    Optional<ExternalEventImpact> findByExternalEventIdAndSupplierId(UUID externalEventId, UUID supplierId);
}
