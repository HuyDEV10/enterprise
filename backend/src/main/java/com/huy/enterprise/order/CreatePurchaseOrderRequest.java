package com.huy.enterprise.order;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.huy.enterprise.common.enums.PurchaseOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePurchaseOrderRequest(
        @NotBlank String orderCode,
        @NotNull UUID supplierId,
        @NotNull LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        PurchaseOrderStatus status,
        String notes,
        @NotNull @Valid List<CreatePurchaseOrderItemRequest> items) {
}
