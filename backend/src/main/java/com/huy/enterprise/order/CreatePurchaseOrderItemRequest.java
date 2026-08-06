import jakarta.validation.constraints.NotNull;

public record CreatePurchaseOrderItemRequest(@NotNull UUID productId, @NotNull BigDecimal quantity,
        @NotNull BigDecimal unitPrice) {

}
