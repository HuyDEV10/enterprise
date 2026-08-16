package com.huy.enterprise.monitoring;

import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class RiskMonitoringResult {
    private final OffsetDateTime evaluatedAt = OffsetDateTime.now();
    private int lowStockDetected;
    private int delayedPurchaseOrders;
    private int delayedShipments;
    private int highRiskSuppliers;
    private int riskEventsCreated;
    private int riskEventsUpdated;
    private int riskEventsResolved;
    private int alertsCreated;
    private int alertsResolved;

    void lowStockDetected() { lowStockDetected++; }
    void delayedPurchaseOrderDetected() { delayedPurchaseOrders++; }
    void delayedShipmentDetected() { delayedShipments++; }
    void highRiskSupplierDetected() { highRiskSuppliers++; }
    void riskEventCreated() { riskEventsCreated++; }
    void riskEventUpdated() { riskEventsUpdated++; }
    void riskEventResolved() { riskEventsResolved++; }
    void alertCreated() { alertsCreated++; }
    void alertResolved() { alertsResolved++; }
}
