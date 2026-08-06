package com.example.enterprise.risk;

import com.example.enterprise.common.enums.ImpactLevel;
import com.example.enterprise.common.enums.RiskEventStatus;
import com.example.enterprise.common.enums.RiskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateRiskEventRequest(
        @NotBlank String title,
        @NotNull RiskType riskType,
        @NotNull ImpactLevel impactLevel,
        String description,
        OffsetDateTime detectedAt,
        RiskEventStatus status,
        UUID supplierId,
        UUID productId,
        UUID purchaseOrderId,
        UUID shipmentId) {
}