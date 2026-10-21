package com.huy.enterprise.order;
import java.time.LocalDate; import java.util.*; import com.huy.enterprise.common.enums.PurchaseOrderStatus; import jakarta.validation.Valid; import jakarta.validation.constraints.*;
public record CreatePurchaseOrderRequest(@NotBlank String orderCode,@NotNull UUID supplierId,@NotNull LocalDate orderDate,LocalDate expectedDeliveryDate,PurchaseOrderStatus status,String notes,@NotNull @Size(min=1) @Valid List<CreatePurchaseOrderItemRequest> items){}
