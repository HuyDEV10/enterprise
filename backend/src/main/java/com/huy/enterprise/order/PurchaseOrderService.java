package com.huy.enterprise.order;

import org.springframework.data.domain.Pageable;

import com.huy.enterprise.audit.AuditService;
import com.huy.enterprise.common.*;
import com.huy.enterprise.common.enums.PurchaseOrderStatus;
import com.huy.enterprise.common.enums.RecordStatus;
import com.huy.enterprise.common.enums.ShipmentStatus;
import com.huy.enterprise.product.Product;
import com.huy.enterprise.product.ProductRepository;
import com.huy.enterprise.shipment.ShipmentRepository;
import com.huy.enterprise.supplier.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderService {
    private final PurchaseOrderRepository repository;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final ShipmentRepository shipments;
    private final AuditService audit;
    private static final List<ShipmentStatus> ACTIVE_SHIPMENTS =
            List.of(ShipmentStatus.PENDING, ShipmentStatus.SHIPPING, ShipmentStatus.DELAYED);

    public List<PurchaseOrderResponse> all() {
        return repository.findAll().stream().map(PurchaseOrderResponse::from).toList();
    }

    public PurchaseOrderResponse one(UUID id) { return PurchaseOrderResponse.from(get(id)); }

    public PageResponse<PurchaseOrderResponse> page(Pageable pageable) {
        var bounded = PageRequests.validate(pageable, "id", "orderCode", "orderDate", "expectedDeliveryDate", "status", "totalAmount", "createdAt", "updatedAt");
        return PageResponse.from(repository.findAll(bounded).map(PurchaseOrderResponse::from));
    }

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {
        if (request.status() != null && request.status() != PurchaseOrderStatus.DRAFT) {
            throw new BusinessException("New purchase orders must start in DRAFT");
        }
        if (repository.existsByOrderCode(request.orderCode())) throw new ConflictException("Purchase order code already exists");
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderCode(request.orderCode());
        order.setStatus(PurchaseOrderStatus.DRAFT);
        populate(order, request.supplierId(), request.orderDate(), request.expectedDeliveryDate(), request.notes(), request.items());
        order = repository.save(order);
        audit.record("PURCHASE_ORDER_CREATED", "PURCHASE_ORDER", order.getId(), "Created in DRAFT; total=" + order.getTotalAmount());
        return PurchaseOrderResponse.from(order);
    }

    @Transactional
    public PurchaseOrderResponse update(UUID id, UpdatePurchaseOrderRequest request) {
        PurchaseOrder order = getForUpdate(id);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ConflictException("Only DRAFT purchase orders can be edited");
        }
        BigDecimal previous = order.getTotalAmount();
        populate(order, request.supplierId(), request.orderDate(), request.expectedDeliveryDate(), request.notes(), request.items());
        audit.record("PURCHASE_ORDER_UPDATED", "PURCHASE_ORDER", id, "Total: " + previous + " -> " + order.getTotalAmount());
        return PurchaseOrderResponse.from(repository.save(order));
    }

    @Transactional
    public PurchaseOrderResponse updateStatus(UUID id, PurchaseOrderStatus next) {
        PurchaseOrder order = getForUpdate(id);
        PurchaseOrderStatus previous = order.getStatus();
        if (!previous.canTransitionTo(next)) throw new ConflictException("Invalid purchase order status transition: " + previous + " -> " + next);
        if (previous == next) return PurchaseOrderResponse.from(order);
        if ((next == PurchaseOrderStatus.CANCELLED || next == PurchaseOrderStatus.DELIVERED)
                && shipments.existsByPurchaseOrderIdAndStatusIn(id, ACTIVE_SHIPMENTS)) {
            throw new ConflictException("Complete or cancel active shipments before closing the purchase order");
        }
        order.setStatus(next);
        audit.record("PURCHASE_ORDER_STATUS_CHANGED", "PURCHASE_ORDER", id, previous + " -> " + next);
        return PurchaseOrderResponse.from(repository.save(order));
    }

    private PurchaseOrder get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }

    private PurchaseOrder getForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }

    private void populate(PurchaseOrder order, UUID supplierId, LocalDate orderDate, LocalDate expected,
                          String notes, List<CreatePurchaseOrderItemRequest> requests) {
        if (expected != null && expected.isBefore(orderDate)) throw new BusinessException("Expected delivery cannot precede order date");
        var supplier = suppliers.findById(supplierId).orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + supplierId));
        if (supplier.getStatus() != RecordStatus.ACTIVE) throw new BusinessException("Supplier is inactive");
        order.setSupplier(supplier);
        order.setOrderDate(orderDate);
        order.setExpectedDeliveryDate(expected);
        order.setNotes(notes);
        order.getItems().clear();
        BigDecimal total = BigDecimal.ZERO;
        for (CreatePurchaseOrderItemRequest request : requests) {
            Product product = products.findById(request.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.productId()));
            if (product.getStatus() != RecordStatus.ACTIVE) throw new BusinessException("Product is inactive");
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(order);
            item.setProduct(product);
            item.setQuantity(request.quantity());
            item.setUnitPrice(request.unitPrice());
            item.setLineTotal(request.quantity().multiply(request.unitPrice()));
            order.getItems().add(item);
            total = total.add(item.getLineTotal());
        }
        order.setTotalAmount(total);
    }
}
