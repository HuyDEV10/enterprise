package com.huy.enterprise.risk;
import com.huy.enterprise.common.enums.*; import jakarta.validation.constraints.*; import java.time.OffsetDateTime; import java.util.UUID;
public record UpdateRiskEventRequest(@NotBlank String title,@NotNull RiskType riskType,@NotNull ImpactLevel impactLevel,String description,@NotNull OffsetDateTime detectedAt,UUID supplierId,UUID productId,UUID purchaseOrderId,UUID shipmentId){}
