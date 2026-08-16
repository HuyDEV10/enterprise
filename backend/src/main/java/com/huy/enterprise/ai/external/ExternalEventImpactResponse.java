package com.huy.enterprise.ai.external;

import com.huy.enterprise.common.enums.ImpactLevel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExternalEventImpactResponse(
        UUID id,
        UUID externalEventId,
        String externalSource,
        String sourceEventId,
        UUID supplierId,
        String supplierCode,
        String supplierName,
        BigDecimal impactScore,
        ImpactLevel impactLevel,
        ExternalEventSignal impactType,
        String explanation,
        OffsetDateTime calculatedAt) {

    static ExternalEventImpactResponse from(ExternalEventImpact impact) {
        return new ExternalEventImpactResponse(
                impact.getId(),
                impact.getExternalEvent().getId(),
                impact.getExternalEvent().getExternalSource(),
                impact.getExternalEvent().getExternalEventId(),
                impact.getSupplier().getId(),
                impact.getSupplier().getSupplierCode(),
                impact.getSupplier().getName(),
                impact.getImpactScore(),
                impact.getImpactLevel(),
                impact.getImpactType(),
                impact.getExplanation(),
                impact.getCalculatedAt());
    }
}
