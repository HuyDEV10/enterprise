package com.huy.enterprise.monitoring;

import java.time.OffsetDateTime;

public record RiskMonitoringSummary(
        boolean enabled,
        OffsetDateTime lastScan,
        long automaticActiveRisks,
        long criticalRisks,
        long highRisks,
        long lowStockRisks,
        long delayedOrderRisks,
        long delayedShipmentRisks,
        long highRiskSupplierRisks) {
}
