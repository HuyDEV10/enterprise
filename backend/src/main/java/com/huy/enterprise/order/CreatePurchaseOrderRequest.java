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
        @Valid List<CreatePurchaseOrderItemRequest> items) {
}
