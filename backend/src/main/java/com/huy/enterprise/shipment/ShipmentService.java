package com.huy.enterprise.shipment;

import com.huy.enterprise.audit.AuditService;
import com.huy.enterprise.common.*;
import com.huy.enterprise.common.enums.PurchaseOrderStatus;
import com.huy.enterprise.common.enums.ShipmentStatus;
import com.huy.enterprise.order.PurchaseOrder;
import com.huy.enterprise.order.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShipmentService {
    private final ShipmentRepository repository;
    private final PurchaseOrderRepository orders;
    private final AuditService audit;

    public List<ShipmentResponse> all() { return repository.findAll().stream().map(ShipmentResponse::from).toList(); }
    public ShipmentResponse one(UUID id) { return ShipmentResponse.from(get(id)); }

    @Transactional
    public ShipmentResponse create(CreateShipmentRequest request) {
        if (request.status() != null && request.status() != ShipmentStatus.PENDING) {
            throw new BusinessException("New shipments must start in PENDING");
        }
        if (repository.existsByShipmentCode(request.shipmentCode())) throw new ConflictException("Shipment code already exists");
        Shipment shipment = new Shipment();
        shipment.setShipmentCode(request.shipmentCode());
        shipment.setStatus(ShipmentStatus.PENDING);
        apply(shipment, request.purchaseOrderId(), request.carrierName(), request.departureDate(),
                request.expectedArrivalDate(), request.actualArrivalDate(), request.currentLocation(), request.notes());
        shipment = repository.save(shipment);
        audit.record("SHIPMENT_CREATED", "SHIPMENT", shipment.getId(), "Created in PENDING");
        return ShipmentResponse.from(shipment);
    }

    @Transactional
    public ShipmentResponse update(UUID id, UpdateShipmentRequest request) {
        Shipment shipment = get(id);
        if (shipment.getStatus().isTerminal()) throw new ConflictException("Completed or cancelled shipments cannot be edited");
        if (shipment.getStatus() != ShipmentStatus.PENDING
                && !shipment.getPurchaseOrder().getId().equals(request.purchaseOrderId())) {
            throw new ConflictException("A shipment already in progress cannot be reassigned to another order");
        }
        apply(shipment, request.purchaseOrderId(), request.carrierName(), request.departureDate(),
                request.expectedArrivalDate(), request.actualArrivalDate(), request.currentLocation(), request.notes());
        audit.record("SHIPMENT_UPDATED", "SHIPMENT", id, "Shipping details updated");
        return ShipmentResponse.from(repository.save(shipment));
    }

    @Transactional
    public ShipmentResponse status(UUID id, ShipmentStatus next) {
        Shipment shipment = get(id);
        PurchaseOrder order = lockOrder(shipment.getPurchaseOrder().getId());
        ShipmentStatus previous = shipment.getStatus();
        if (!previous.canTransitionTo(next)) throw new ConflictException("Invalid shipment status transition: " + previous + " -> " + next);
        if (previous == next) return ShipmentResponse.from(shipment);
        if (next != ShipmentStatus.CANCELLED) requireActiveOrder(order);
        if (next == ShipmentStatus.SHIPPING) {
            if (shipment.getDepartureDate() == null) shipment.setDepartureDate(LocalDate.now());
            if (shipment.getDepartureDate().isAfter(LocalDate.now())) throw new BusinessException("Departure date is still in the future");
            if (order.getStatus() == PurchaseOrderStatus.ORDERED) {
                order.setStatus(PurchaseOrderStatus.IN_TRANSIT);
                audit.record("PURCHASE_ORDER_STATUS_CHANGED", "PURCHASE_ORDER", order.getId(), "ORDERED -> IN_TRANSIT (shipment started)");
            }
        }
        if (next == ShipmentStatus.ARRIVED) {
            if (shipment.getDepartureDate() != null && shipment.getDepartureDate().isAfter(LocalDate.now())) {
                throw new BusinessException("Arrival cannot precede departure");
            }
            shipment.setActualArrivalDate(LocalDate.now());
        }
        shipment.setStatus(next);
        audit.record("SHIPMENT_STATUS_CHANGED", "SHIPMENT", id, previous + " -> " + next);
        return ShipmentResponse.from(repository.save(shipment));
    }

    private Shipment get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + id));
    }

    private PurchaseOrder lockOrder(UUID id) {
        return orders.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }

    private void requireActiveOrder(PurchaseOrder order) {
        if (!List.of(PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.IN_TRANSIT, PurchaseOrderStatus.DELAYED).contains(order.getStatus())) {
            throw new ConflictException("Shipments require an active, placed purchase order");
        }
    }

    private void apply(Shipment shipment, UUID orderId, String carrier, LocalDate departure, LocalDate expected,
                       LocalDate actual, String location, String notes) {
        PurchaseOrder order = lockOrder(orderId);
        requireActiveOrder(order);
        if ((departure != null && departure.isBefore(order.getOrderDate()))
                || (expected != null && expected.isBefore(order.getOrderDate()))
                || (departure != null && expected != null && expected.isBefore(departure))) {
            throw new BusinessException("Shipment dates must follow the order date and arrival cannot precede departure");
        }
        if (actual != null) throw new BusinessException("Actual arrival is recorded by the ARRIVED status transition");
        shipment.setPurchaseOrder(order);
        shipment.setCarrierName(carrier);
        shipment.setDepartureDate(departure);
        shipment.setExpectedArrivalDate(expected);
        shipment.setCurrentLocation(location);
        shipment.setNotes(notes);
    }
}
