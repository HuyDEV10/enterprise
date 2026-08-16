package com.huy.enterprise.ai.inventory;

import com.huy.enterprise.ai.demand.DemandSeriesMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ai-service", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiInventoryRiskScheduler {
    private final DemandSeriesMappingRepository mappings;
    private final PredictiveInventoryRiskService predictiveRisks;

    @Scheduled(cron = "${app.ai-service.inventory-risk-cron:0 */15 * * * *}")
    public void refreshMappedProducts() {
        for (UUID productId : mappings.findActiveProductIds()) {
            try {
                predictiveRisks.refresh(productId);
            } catch (Exception ex) {
                log.warn("AI inventory risk refresh skipped for product {}: {}", productId, ex.getMessage());
            }
        }
    }
}
