package com.huy.enterprise.inventory;
import java.math.BigDecimal; import java.util.UUID; import jakarta.validation.constraints.*;
public record InventoryRequest(@NotNull UUID warehouseId,@NotNull UUID productId,@NotNull @PositiveOrZero BigDecimal quantity,@NotNull @PositiveOrZero BigDecimal lowStockThreshold){}
