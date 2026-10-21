package com.huy.enterprise.supplier;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.huy.enterprise.common.enums.*;

public record SupplierResponse(UUID id, String supplierCode, String name, String email, String phone,
        String address, String country, String region, RecordStatus status, RiskLevel riskLevel,
        String notes, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static SupplierResponse from(Supplier s) {
        return new SupplierResponse(s.getId(), s.getSupplierCode(), s.getName(), s.getEmail(), s.getPhone(),
                s.getAddress(), s.getCountry(), s.getRegion(), s.getStatus(), s.getRiskLevel(), s.getNotes(),
                s.getCreatedAt(), s.getUpdatedAt());
    }
}
