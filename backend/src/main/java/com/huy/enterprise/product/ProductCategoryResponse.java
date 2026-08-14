package com.huy.enterprise.product;

import java.util.UUID;
import com.huy.enterprise.common.enums.RecordStatus;

public record ProductCategoryResponse(UUID id, String name, String code, String description, RecordStatus status) {
    static ProductCategoryResponse from(ProductCategory c){return new ProductCategoryResponse(c.getId(),c.getName(),c.getCode(),c.getDescription(),c.getStatus());}
}
