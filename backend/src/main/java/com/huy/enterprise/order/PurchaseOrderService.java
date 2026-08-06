package com.huy.enterprise.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.huy.enterprise.common.ResourceNotFoundException;
import com.huy.enterprise.common.enums.PurchaseOrderStatus;
import com.huy.enterprise.product.Product;
import com.huy.enterprise.product.ProductRepository;
import com.huy.enterprise.supplier.Supplier;
import com.huy.enterprise.supplier.SupplierRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public List<PurchaseOrder> findAll() {
        return purchaseOrderRepository.findAll();
    }

    public PurchaseOrder findById(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }

    public PurchaseOrder create(CreatePurchaseOrderRequest request) {
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + request.supplierId()));

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setOrderCode(request.orderCode());
        purchaseOrder.setSupplier(supplier);
        purchaseOrder.setOrderDate(request.orderDate());
        purchaseOrder.setExpectedDeliveryDate(request.expectedDeliveryDate());
        purchaseOrder.setStatus(request.status() == null ? PurchaseOrderStatus.DRAFT : request.status());
        purchaseOrder.setNotes(request.notes());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CreatePurchaseOrderItemRequest itemRequest : request.items()) {
            if (itemRequest.quantity().signum() <= 0 || itemRequest.unitPrice().signum() <= 0) {
                throw new IllegalArgumentException("Purchase order item quantity and unit price must be positive");
            }
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.productId()));
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(purchaseOrder);
            item.setProduct(product);
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(itemRequest.unitPrice());
            item.setLineTotal(itemRequest.quantity().multiply(itemRequest.unitPrice()));
            purchaseOrder.getItems().add(item);
            totalAmount = totalAmount.add(item.getLineTotal());
        }
        purchaseOrder.setTotalAmount(totalAmount);
        return purchaseOrderRepository.save(purchaseOrder);
    }
}
