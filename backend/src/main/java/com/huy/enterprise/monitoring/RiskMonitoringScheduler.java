package com.huy.enterprise.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.risk-monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RiskMonitoringScheduler {
    private final RiskMonitoringService monitoringService;

    @Scheduled(cron = "${app.risk-monitoring.cron:0 */5 * * * *}")
    public void scan() {
        try {
            RiskMonitoringResult result = monitoringService.runMonitoring();
            log.info(
                    "Risk monitoring completed at {}: created={}, updated={}, resolved={}, alertsCreated={}, alertsResolved={}",
                    result.getEvaluatedAt(),
                    result.getRiskEventsCreated(),
                    result.getRiskEventsUpdated(),
                    result.getRiskEventsResolved(),
                    result.getAlertsCreated(),
                    result.getAlertsResolved());
        } catch (RuntimeException ex) {
            log.error("Risk monitoring scheduled scan failed", ex);
        }
    }
}
