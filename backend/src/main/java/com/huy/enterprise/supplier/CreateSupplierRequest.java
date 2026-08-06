package com.example.enterprise.supplier;

import com.example.enterprise.common.enums.RecordStatus;
import com.example.enterprise.common.enums.RiskLevel;
import jakarta.validation.constraints.NotBlank;

public record CreateSupplierRequest(
        @NotBlank String supplierCode,
        @NotBlank String name,
        String email,
        String phone,
        String address,
        String country,
        String region,
        RecordStatus status,
        RiskLevel riskLevel,
        String notes) {
}