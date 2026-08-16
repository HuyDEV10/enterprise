package com.huy.enterprise.ai.inventory;

import com.huy.enterprise.common.enums.ImpactLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryForecastServiceTest {
    private final LocalDate today = LocalDate.of(2026, 8, 16);

    @Test
    void stockoutWithinSevenDaysIsCritical() {
        ImpactLevel result = InventoryForecastService.classifyRisk(
                today, today.plusDays(7), BigDecimal.valueOf(-1), BigDecimal.TEN);
        assertThat(result).isEqualTo(ImpactLevel.CRITICAL);
    }

    @Test
    void stockoutAfterSevenDaysWithinHorizonIsHigh() {
        ImpactLevel result = InventoryForecastService.classifyRisk(
                today, today.plusDays(8), BigDecimal.valueOf(-1), BigDecimal.TEN);
        assertThat(result).isEqualTo(ImpactLevel.HIGH);
    }

    @Test
    void lowProjectedInventoryWithoutStockoutIsMedium() {
        ImpactLevel result = InventoryForecastService.classifyRisk(
                today, null, BigDecimal.valueOf(8), BigDecimal.TEN);
        assertThat(result).isEqualTo(ImpactLevel.MEDIUM);
    }

    @Test
    void healthyProjectedInventoryIsLow() {
        ImpactLevel result = InventoryForecastService.classifyRisk(
                today, null, BigDecimal.valueOf(20), BigDecimal.TEN);
        assertThat(result).isEqualTo(ImpactLevel.LOW);
    }
}
