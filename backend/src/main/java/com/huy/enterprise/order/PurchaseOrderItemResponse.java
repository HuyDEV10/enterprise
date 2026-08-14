package com.huy.enterprise.order;
import java.math.BigDecimal; import java.util.UUID;
public record PurchaseOrderItemResponse(UUID id,UUID productId,String productName,BigDecimal quantity,BigDecimal unitPrice,BigDecimal lineTotal){static PurchaseOrderItemResponse from(PurchaseOrderItem i){return new PurchaseOrderItemResponse(i.getId(),i.getProduct().getId(),i.getProduct().getName(),i.getQuantity(),i.getUnitPrice(),i.getLineTotal());}}
