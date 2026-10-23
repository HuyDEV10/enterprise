package com.huy.enterprise.ai.external;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalEventSentimentRepository extends JpaRepository<ExternalEventSentiment, UUID> {
    Optional<ExternalEventSentiment> findByExternalEventIdAndTargetKey(UUID externalEventId, String targetKey);
    @EntityGraph(attributePaths="supplier")
    List<ExternalEventSentiment> findByExternalEventIdOrderByAnalyzedAtDesc(UUID externalEventId);
}
