package com.huy.enterprise.product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.huy.enterprise.common.enums.RecordStatus;

public record ProductResponse(UUID id, String productCode, String name, String unit, BigDecimal referencePrice,
        String description, RecordStatus status, UUID categoryId, String categoryName, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static ProductResponse from(Product p) {
        ProductCategory c = p.getCategory();
        return new ProductResponse(p.getId(), p.getProductCode(), p.getName(), p.getUnit(), p.getReferencePrice(), p.getDescription(),
                p.getStatus(), c == null ? null : c.getId(), c == null ? null : c.getName(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
