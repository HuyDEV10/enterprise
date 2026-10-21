package com.huy.enterprise.supplier;

import com.huy.enterprise.common.enums.*;
import jakarta.validation.constraints.*;

public record UpdateSupplierRequest(
        @NotBlank String name,
        @Email String email,
        String phone,
        String address,
        String country,
        String region,
        RecordStatus status,
        RiskLevel riskLevel,
        String notes) {}
