package com.huy.enterprise.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/risk-monitoring")
@RequiredArgsConstructor
public class RiskMonitoringController {
    private final RiskMonitoringService monitoringService;

    @PostMapping("/run")
    public RiskMonitoringResult run() {
        return monitoringService.runMonitoring();
    }

    @GetMapping("/summary")
    public RiskMonitoringSummary summary() {
        return monitoringService.summary();
    }

    @GetMapping("/rules")
    public List<RiskRuleInfo> rules() {
        return List.of(
                new RiskRuleInfo(RiskRuleCode.LOW_STOCK, "Inventory quantity is at or below its low-stock threshold."),
                new RiskRuleInfo(RiskRuleCode.DELAYED_PURCHASE_ORDER, "Purchase order expected delivery date has passed while the order is still active."),
                new RiskRuleInfo(RiskRuleCode.DELAYED_SHIPMENT, "Shipment expected arrival date has passed while the shipment is still active."),
                new RiskRuleInfo(RiskRuleCode.HIGH_RISK_SUPPLIER, "Supplier risk level is HIGH."));
    }

    public record RiskRuleInfo(RiskRuleCode code, String description) {
    }
}
