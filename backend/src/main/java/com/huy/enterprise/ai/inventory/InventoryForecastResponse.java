package com.huy.enterprise.ai.inventory;

import com.huy.enterprise.common.enums.ImpactLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InventoryForecastResponse(
        UUID productId,
        String productCode,
        int historyDays,
        String modelVersion,
        BigDecimal currentInventory,
        BigDecimal lowStockThreshold,
        HorizonValues forecastDemand,
        HorizonValues incomingSupply,
        HorizonValues projectedInventory,
        ImpactLevel stockoutRisk,
        LocalDate projectedStockoutDate,
        List<DailyProjection> dailyProjection) {

    public record HorizonValues(BigDecimal next7Days, BigDecimal next14Days, BigDecimal next28Days) {
    }

    public record DailyProjection(
            LocalDate date,
            BigDecimal forecastDemand,
            BigDecimal incomingSupply,
            BigDecimal projectedInventory) {
    }
}
