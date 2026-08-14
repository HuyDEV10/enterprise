package com.huy.enterprise.product;

import java.math.BigDecimal;
import java.util.UUID;
import com.huy.enterprise.common.enums.RecordStatus;
import jakarta.validation.constraints.*;

public record UpdateProductRequest(UUID categoryId, @NotBlank String name, @NotBlank String unit,
        @DecimalMin(value="0.0", inclusive=true) BigDecimal referencePrice, String description, RecordStatus status) {}
