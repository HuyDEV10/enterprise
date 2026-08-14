package com.huy.enterprise.order;
import com.huy.enterprise.common.enums.PurchaseOrderStatus; import jakarta.validation.constraints.NotNull;
public record UpdatePurchaseOrderStatusRequest(@NotNull PurchaseOrderStatus status){}
