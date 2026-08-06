package com.huy.enterprise.risk;

import com.huy.enterprise.common.ResourceNotFoundException;
import com.huy.enterprise.common.enums.RiskEventStatus;
import com.huy.enterprise.order.PurchaseOrderRepository;
import com.huy.enterprise.product.ProductRepository;
import com.huy.enterprise.shipment.ShipmentRepository;
import com.huy.enterprise.supplier.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RiskEventService {
    private final RiskEventRepository riskEventRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ShipmentRepository shipmentRepository;

    public List<RiskEvent> findAll() {
        return riskEventRepository.findAll();
    }

    public RiskEvent findById(UUID id) {
        return riskEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk event not found: " + id));
    }

    public RiskEvent create(CreateRiskEventRequest request) {
        RiskEvent r = new RiskEvent();
        r.setTitle(request.title());
        r.setRiskType(request.riskType());
        r.setImpactLevel(request.impactLevel());
        r.setDescription(request.description());
        r.setDetectedAt(request.detectedAt() == null ? OffsetDateTime.now() : request.detectedAt());
        r.setStatus(request.status() == null ? RiskEventStatus.OPEN : request.status());
        if (request.supplierId() != null)
            r.setSupplier(supplierRepository.findById(request.supplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + request.supplierId())));
        if (request.productId() != null)
            r.setProduct(productRepository.findById(request.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.productId())));
        if (request.purchaseOrderId() != null)
            r.setPurchaseOrder(purchaseOrderRepository.findById(request.purchaseOrderId()).orElseThrow(
                    () -> new ResourceNotFoundException("Purchase order not found: " + request.purchaseOrderId())));
        if (request.shipmentId() != null)
            r.setShipment(shipmentRepository.findById(request.shipmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + request.shipmentId())));
        return riskEventRepository.save(r);
    }
}