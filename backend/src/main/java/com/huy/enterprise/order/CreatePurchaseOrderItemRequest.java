package com.huy.enterprise.order;
import java.math.BigDecimal; import java.util.UUID; import jakarta.validation.constraints.*;
public record CreatePurchaseOrderItemRequest(@NotNull UUID productId,@NotNull @Positive BigDecimal quantity,@NotNull @PositiveOrZero BigDecimal unitPrice){}
