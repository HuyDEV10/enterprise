package com.huy.enterprise.ai.external;

import com.huy.enterprise.alert.Alert;
import com.huy.enterprise.alert.AlertRepository;
import com.huy.enterprise.common.ResourceNotFoundException;
import com.huy.enterprise.common.enums.AlertPriority;
import com.huy.enterprise.common.enums.AlertStatus;
import com.huy.enterprise.common.enums.ImpactLevel;
import com.huy.enterprise.common.enums.PurchaseOrderStatus;
import com.huy.enterprise.common.enums.RiskEventSource;
import com.huy.enterprise.common.enums.RiskEventStatus;
import com.huy.enterprise.common.enums.RiskType;
import com.huy.enterprise.common.enums.ShipmentStatus;
import com.huy.enterprise.inventory.InventoryItemRepository;
import com.huy.enterprise.monitoring.RiskRuleCode;
import com.huy.enterprise.order.PurchaseOrder;
import com.huy.enterprise.order.PurchaseOrderItem;
import com.huy.enterprise.order.PurchaseOrderItemRepository;
import com.huy.enterprise.order.PurchaseOrderRepository;
import com.huy.enterprise.product.Product;
import com.huy.enterprise.risk.RiskEvent;
import com.huy.enterprise.risk.RiskEventRepository;
import com.huy.enterprise.shipment.Shipment;
import com.huy.enterprise.shipment.ShipmentRepository;
import com.huy.enterprise.supplier.Supplier;
import com.huy.enterprise.supplier.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExternalEventImpactService {
    private static final Set<PurchaseOrderStatus> ACTIVE_ORDER_STATUSES = EnumSet.of(
            PurchaseOrderStatus.ORDERED,
            PurchaseOrderStatus.IN_TRANSIT,
            PurchaseOrderStatus.DELAYED);
    private static final Set<ShipmentStatus> ACTIVE_SHIPMENT_STATUSES = EnumSet.of(
            ShipmentStatus.PENDING,
            ShipmentStatus.SHIPPING,
            ShipmentStatus.DELAYED);
    private static final List<RiskEventStatus> ACTIVE_RISK_STATUSES = List.of(
            RiskEventStatus.OPEN,
            RiskEventStatus.INVESTIGATING);
    private static final List<AlertStatus> ACTIVE_ALERT_STATUSES = List.of(
            AlertStatus.NEW,
            AlertStatus.READ);

    private final ExternalEventRepository events;
    private final ExternalEventImpactRepository impacts;
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository purchaseOrders;
    private final PurchaseOrderItemRepository purchaseOrderItems;
    private final ShipmentRepository shipments;
    private final InventoryItemRepository inventoryItems;
    private final RiskEventRepository riskEvents;
    private final AlertRepository alerts;

    @Transactional
    public List<ExternalEventImpactResponse> calculate(UUID eventId) {
        ExternalEvent event = events.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("External event not found: " + eventId));

        List<PurchaseOrder> allOrders = purchaseOrders.findAll();
        List<PurchaseOrderItem> allOrderItems = purchaseOrderItems.findAll();
        List<Shipment> allShipments = shipments.findAll();
        List<ExternalEventImpactResponse> result = new ArrayList<>();

        for (Supplier supplier : suppliers.findAll()) {
            Exposure exposure = exposureFor(supplier, allOrders, allOrderItems, allShipments);
            BusinessExposureScoring.Result scoring = BusinessExposureScoring.calculate(
                    scoringInput(event, supplier, exposure));

            ExternalEventImpact impact = impacts
                    .findByExternalEventIdAndSupplierId(eventId, supplier.getId())
                    .orElse(null);

            if (!scoring.relevant()) {
                if (impact != null) {
                    impacts.delete(impact);
                }
                resolveRisk(event, supplier);
                continue;
            }

            if (impact == null) {
                impact = new ExternalEventImpact();
                impact.setExternalEvent(event);
                impact.setSupplier(supplier);
            }
            impact.setImpactScore(scoring.score());
            impact.setImpactLevel(scoring.impactLevel());
            impact.setImpactType(scoring.signal());
            impact.setExplanation(explanation(scoring, exposure));
            impact.setCalculatedAt(OffsetDateTime.now());
            impact = impacts.save(impact);
            result.add(ExternalEventImpactResponse.from(impact));

            String sourceRef = sourceRef(event, supplier);
            if (scoring.signal() == ExternalEventSignal.RISK
                    && (scoring.impactLevel() == ImpactLevel.HIGH
                    || scoring.impactLevel() == ImpactLevel.CRITICAL)) {
                ensureRisk(event, supplier, impact, sourceRef);
            } else {
                resolveRisk(event, supplier);
            }
        }

        event.setSignal(result.stream().anyMatch(i -> i.impactType() == ExternalEventSignal.RISK)
                ? ExternalEventSignal.RISK
                : result.stream().anyMatch(i -> i.impactType() == ExternalEventSignal.OPPORTUNITY_SIGNAL)
                        ? ExternalEventSignal.OPPORTUNITY_SIGNAL
                        : ExternalEventSignal.NEUTRAL);
        events.save(event);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ExternalEventImpactResponse> byEvent(UUID eventId) {
        if (!events.existsById(eventId)) {
            throw new ResourceNotFoundException("External event not found: " + eventId);
        }
        return impacts.findByExternalEventId(eventId).stream()
                .map(ExternalEventImpactResponse::from)
                .toList();
    }

    private Exposure exposureFor(
            Supplier supplier,
            List<PurchaseOrder> allOrders,
            List<PurchaseOrderItem> allOrderItems,
            List<Shipment> allShipments) {
        List<PurchaseOrder> activeOrders = allOrders.stream()
                .filter(order -> order.getSupplier().getId().equals(supplier.getId()))
                .filter(order -> ACTIVE_ORDER_STATUSES.contains(order.getStatus()))
                .toList();
        Set<UUID> orderIds = activeOrders.stream()
                .map(PurchaseOrder::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<Shipment> activeShipments = allShipments.stream()
                .filter(shipment -> orderIds.contains(shipment.getPurchaseOrder().getId()))
                .filter(shipment -> ACTIVE_SHIPMENT_STATUSES.contains(shipment.getStatus()))
                .toList();

        Set<Product> affectedProducts = new LinkedHashSet<>();
        for (PurchaseOrderItem item : allOrderItems) {
            if (orderIds.contains(item.getPurchaseOrder().getId())) {
                affectedProducts.add(item.getProduct());
            }
        }

        int inventoryCovered = 0;
        int lowStock = 0;
        for (Product product : affectedProducts) {
            if (!inventoryItems.existsByProductId(product.getId())) {
                continue;
            }
            inventoryCovered++;
            BigDecimal quantity = inventoryItems.sumQuantityByProductId(product.getId());
            BigDecimal threshold = inventoryItems.sumLowStockThresholdByProductId(product.getId());
            BigDecimal safeQuantity = quantity == null ? BigDecimal.ZERO : quantity;
            BigDecimal safeThreshold = threshold == null ? BigDecimal.ZERO : threshold;
            if (safeQuantity.compareTo(safeThreshold) <= 0) {
                lowStock++;
            }
        }
        return new Exposure(activeOrders.size(), activeShipments.size(), affectedProducts.size(), inventoryCovered, lowStock);
    }

    private static BusinessExposureScoring.Input scoringInput(
            ExternalEvent event,
            Supplier supplier,
            Exposure exposure) {
        return new BusinessExposureScoring.Input(
                event.getEventRootCode(),
                event.getGoldsteinScore(),
                event.getAvgTone(),
                event.getCountryCode(),
                event.getCountry(),
                event.getLocation(),
                event.getActor1Name(),
                event.getActor2Name(),
                event.getSentiment(),
                supplier.getSupplierCode(),
                supplier.getName(),
                supplier.getCountry(),
                supplier.getRegion(),
                supplier.getAddress(),
                exposure.activeOrders(),
                exposure.activeShipments(),
                exposure.affectedProducts(),
                exposure.inventoryCoveredProducts(),
                exposure.lowStockProducts());
    }

    private static String explanation(BusinessExposureScoring.Result scoring, Exposure exposure) {
        StringBuilder builder = new StringBuilder();
        builder.append("Transparent business exposure score=")
                .append(scoring.score())
                .append("/100; level=")
                .append(scoring.impactLevel())
                .append("; signal=")
                .append(scoring.signal())
                .append(". Exposure: activePOs=")
                .append(exposure.activeOrders())
                .append(", activeShipments=")
                .append(exposure.activeShipments())
                .append(", affectedProducts=")
                .append(exposure.affectedProducts())
                .append(", inventoryCoveredProducts=")
                .append(exposure.inventoryCoveredProducts())
                .append(", lowStockProducts=")
                .append(exposure.lowStockProducts())
                .append(". Components: ");
        for (Map.Entry<String, BusinessExposureScoring.Component> entry : scoring.components().entrySet()) {
            builder.append(entry.getKey())
                    .append("[weight=")
                    .append(entry.getValue().weight())
                    .append(", factor=")
                    .append(entry.getValue().factor() == null
                            ? "N/A"
                            : String.format(java.util.Locale.ROOT, "%.2f", entry.getValue().factor()))
                    .append(", reason=")
                    .append(entry.getValue().reason())
                    .append("]; ");
        }
        builder.append("Missing components are excluded and available weights are renormalized. "
                + "This is a business rule score, not a supervised ML prediction.");
        return builder.toString();
    }

    private void ensureRisk(
            ExternalEvent event,
            Supplier supplier,
            ExternalEventImpact impact,
            String sourceRef) {
        RiskEvent risk = riskEvents
                .findFirstByAutoGeneratedTrueAndRuleCodeAndSourceRefAndStatusIn(
                        RiskRuleCode.EXTERNAL_EVENT_IMPACT,
                        sourceRef,
                        ACTIVE_RISK_STATUSES)
                .orElse(null);

        String title = "External event exposure - " + supplier.getSupplierCode();
        String description = "External event " + event.getExternalEventId()
                + " creates a " + impact.getImpactLevel()
                + " business exposure for supplier " + supplier.getSupplierCode()
                + " with score " + impact.getImpactScore() + "/100. "
                + impact.getExplanation();

        if (risk == null) {
            risk = new RiskEvent();
            risk.setSource(RiskEventSource.EXTERNAL_EVENT);
            risk.setAutoGenerated(true);
            risk.setRuleCode(RiskRuleCode.EXTERNAL_EVENT_IMPACT);
            risk.setSourceRef(sourceRef);
            risk.setStatus(RiskEventStatus.OPEN);
            risk.setDetectedAt(OffsetDateTime.now());
        }
        risk.setRiskType(RiskType.SUPPLIER);
        risk.setSupplier(supplier);
        risk.setImpactLevel(impact.getImpactLevel());
        risk.setTitle(title);
        risk.setDescription(description);
        risk = riskEvents.save(risk);
        ensureAlert(risk);
    }

    private void ensureAlert(RiskEvent risk) {
        Alert alert = alerts.findFirstByRiskEventIdAndStatusIn(risk.getId(), ACTIVE_ALERT_STATUSES)
                .orElse(null);
        AlertPriority priority = AlertPriority.valueOf(risk.getImpactLevel().name());
        if (alert == null) {
            alert = new Alert();
            alert.setRiskEvent(risk);
            alert.setStatus(AlertStatus.NEW);
        }
        alert.setAlertType(RiskRuleCode.EXTERNAL_EVENT_IMPACT.name());
        alert.setTitle(risk.getTitle());
        alert.setMessage(risk.getDescription());
        alert.setPriority(priority);
        alerts.save(alert);
    }

    private void resolveRisk(ExternalEvent event, Supplier supplier) {
        riskEvents.findFirstByAutoGeneratedTrueAndRuleCodeAndSourceRefAndStatusIn(
                        RiskRuleCode.EXTERNAL_EVENT_IMPACT,
                        sourceRef(event, supplier),
                        ACTIVE_RISK_STATUSES)
                .ifPresent(risk -> {
                    risk.setStatus(RiskEventStatus.RESOLVED);
                    riskEvents.save(risk);
                    OffsetDateTime resolvedAt = OffsetDateTime.now();
                    for (Alert alert : alerts.findByRiskEventIdAndStatusIn(risk.getId(), ACTIVE_ALERT_STATUSES)) {
                        alert.setStatus(AlertStatus.RESOLVED);
                        alert.setResolvedAt(resolvedAt);
                        alerts.save(alert);
                    }
                });
    }

    private static String sourceRef(ExternalEvent event, Supplier supplier) {
        return "EXTERNAL_EVENT:" + event.getId() + ":" + supplier.getId();
    }

    private record Exposure(
            int activeOrders,
            int activeShipments,
            int affectedProducts,
            int inventoryCoveredProducts,
            int lowStockProducts) {
    }
}
