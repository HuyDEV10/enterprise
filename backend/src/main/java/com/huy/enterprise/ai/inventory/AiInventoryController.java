package com.huy.enterprise.ai.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai/inventory/products")
@RequiredArgsConstructor
public class AiInventoryController {
    private final InventoryForecastService forecasts;
    private final DemandDataImportService imports;
    private final PredictiveInventoryRiskService predictiveRisks;

    @GetMapping("/{productId}/forecast")
    public InventoryForecastResponse forecast(@PathVariable UUID productId) {
        return forecasts.forecast(productId);
    }

    @PostMapping("/{productId}/demand/import-m5")
    public DemandImportResponse importM5(
            @PathVariable UUID productId,
            @RequestParam String itemId,
            @RequestParam(defaultValue = "CA_1") String storeId) {
        return imports.importM5(productId, itemId, storeId);
    }

    @PostMapping("/{productId}/risk/refresh")
    public InventoryForecastResponse refreshRisk(@PathVariable UUID productId) {
        return predictiveRisks.refresh(productId);
    }
}
