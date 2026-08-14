package com.huy.enterprise.order;
import java.time.LocalDate; import java.util.*; import jakarta.validation.Valid; import jakarta.validation.constraints.*;
public record UpdatePurchaseOrderRequest(@NotNull UUID supplierId,@NotNull LocalDate orderDate,LocalDate expectedDeliveryDate,String notes,@NotNull @Size(min=1) @Valid List<CreatePurchaseOrderItemRequest> items){}
