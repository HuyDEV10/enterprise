package com.huy.enterprise.inventory;
import java.math.BigDecimal; import java.time.OffsetDateTime; import java.util.UUID;
public record InventoryResponse(UUID id,UUID warehouseId,String warehouseName,UUID productId,String productName,BigDecimal quantity,BigDecimal lowStockThreshold,OffsetDateTime lastUpdatedAt){static InventoryResponse from(InventoryItem i){return new InventoryResponse(i.getId(),i.getWarehouse().getId(),i.getWarehouse().getName(),i.getProduct().getId(),i.getProduct().getName(),i.getQuantity(),i.getLowStockThreshold(),i.getLastUpdated());}}
