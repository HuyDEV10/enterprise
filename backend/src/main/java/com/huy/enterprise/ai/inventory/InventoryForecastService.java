package com.huy.enterprise.ai.inventory;

import com.huy.enterprise.ai.client.AiServiceClient;
import com.huy.enterprise.ai.demand.DemandHistory;
import com.huy.enterprise.ai.demand.DemandHistoryRepository;
import com.huy.enterprise.ai.demand.DemandSeriesMapping;
import com.huy.enterprise.ai.demand.DemandSeriesMappingRepository;
import com.huy.enterprise.common.BusinessException;
import com.huy.enterprise.common.ResourceNotFoundException;
import com.huy.enterprise.common.enums.ImpactLevel;
import com.huy.enterprise.common.enums.PurchaseOrderStatus;
import com.huy.enterprise.common.enums.ShipmentStatus;
import com.huy.enterprise.inventory.InventoryItemRepository;
import com.huy.enterprise.order.PurchaseOrderItem;
import com.huy.enterprise.order.PurchaseOrderItemRepository;
import com.huy.enterprise.product.Product;
import com.huy.enterprise.product.ProductRepository;
import com.huy.enterprise.shipment.Shipment;
import com.huy.enterprise.shipment.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryForecastService {
    private static final int MIN_HISTORY_DAYS = 56;
    private static final int MAX_HISTORY_SENT = 365;
    private static final Set<PurchaseOrderStatus> ACTIVE_ORDER_STATUSES = EnumSet.of(
            PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.IN_TRANSIT, PurchaseOrderStatus.DELAYED);
    private static final Set<ShipmentStatus> ACTIVE_SHIPMENT_STATUSES = EnumSet.of(
            ShipmentStatus.PENDING, ShipmentStatus.SHIPPING, ShipmentStatus.DELAYED);

    private final ProductRepository products;
    private final DemandSeriesMappingRepository mappings;
    private final DemandHistoryRepository demandHistory;
    private final InventoryItemRepository inventoryItems;
    private final PurchaseOrderItemRepository purchaseOrderItems;
    private final ShipmentRepository shipments;
    private final AiServiceClient aiClient;

    @Transactional(readOnly = true)
    public InventoryForecastResponse forecast(UUID productId) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        DemandSeriesMapping mapping = mappings.findFirstByProductIdAndActiveTrue(productId)
                .orElseThrow(() -> new BusinessException("Product has no active demand-series mapping"));

        List<DemandHistory> allHistory = demandHistory.findByMappingIdOrderByDemandDateAsc(mapping.getId());
        if (allHistory.size() < MIN_HISTORY_DAYS) {
            throw new BusinessException("INSUFFICIENT_HISTORY: at least " + MIN_HISTORY_DAYS
                    + " contiguous demand-history days are required");
        }
        List<DemandHistory> history = allHistory.size() > MAX_HISTORY_SENT
                ? allHistory.subList(allHistory.size() - MAX_HISTORY_SENT, allHistory.size())
                : allHistory;

        List<AiServiceClient.DemandHistoryPoint> historyRequest = history.stream()
                .map(row -> new AiServiceClient.DemandHistoryPoint(
                        row.getDemandDate(), row.getQuantity().doubleValue()))
                .toList();

        LocalDate today = LocalDate.now();
        AiServiceClient.DemandForecastResponse aiForecast = aiClient.forecastDemand(
                historyRequest, today.plusDays(1));

        BigDecimal currentInventory = zeroIfNull(inventoryItems.sumQuantityByProductId(productId));
        BigDecimal lowStockThreshold = zeroIfNull(inventoryItems.sumLowStockThresholdByProductId(productId));
        Map<LocalDate, BigDecimal> incomingByDate = incomingSupplyByDate(productId, today, today.plusDays(28));

        BigDecimal projected = currentInventory;
        List<InventoryForecastResponse.DailyProjection> daily = new ArrayList<>();
        LocalDate stockoutDate = null;

        for (AiServiceClient.DemandForecastPoint point : aiForecast.forecast()) {
            BigDecimal demand = decimal(point.quantity());
            BigDecimal incoming = incomingByDate.getOrDefault(point.date(), BigDecimal.ZERO);
            projected = projected.add(incoming).subtract(demand);
            if (stockoutDate == null && projected.compareTo(BigDecimal.ZERO) <= 0) {
                stockoutDate = point.date();
            }
            daily.add(new InventoryForecastResponse.DailyProjection(
                    point.date(), demand, incoming, projected.setScale(4, RoundingMode.HALF_UP)));
        }

        BigDecimal forecast7 = decimal(aiForecast.summary().next7Days());
        BigDecimal forecast14 = decimal(aiForecast.summary().next14Days());
        BigDecimal forecast28 = decimal(aiForecast.summary().next28Days());
        BigDecimal incoming7 = sumIncoming(incomingByDate, today.plusDays(1), today.plusDays(7));
        BigDecimal incoming14 = sumIncoming(incomingByDate, today.plusDays(1), today.plusDays(14));
        BigDecimal incoming28 = sumIncoming(incomingByDate, today.plusDays(1), today.plusDays(28));
        BigDecimal projected7 = currentInventory.add(incoming7).subtract(forecast7);
        BigDecimal projected14 = currentInventory.add(incoming14).subtract(forecast14);
        BigDecimal projected28 = currentInventory.add(incoming28).subtract(forecast28);

        ImpactLevel risk = classifyRisk(today, stockoutDate, projected28, lowStockThreshold);
        return new InventoryForecastResponse(
                productId,
                product.getProductCode(),
                aiForecast.historyDays(),
                aiForecast.modelVersion(),
                currentInventory,
                lowStockThreshold,
                new InventoryForecastResponse.HorizonValues(forecast7, forecast14, forecast28),
                new InventoryForecastResponse.HorizonValues(incoming7, incoming14, incoming28),
                new InventoryForecastResponse.HorizonValues(projected7, projected14, projected28),
                risk,
                stockoutDate,
                daily);
    }

    private Map<LocalDate, BigDecimal> incomingSupplyByDate(UUID productId, LocalDate today, LocalDate horizonEnd) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        for (PurchaseOrderItem item : purchaseOrderItems.findIncomingItems(productId, ACTIVE_ORDER_STATUSES)) {
            LocalDate arrivalDate = item.getPurchaseOrder().getExpectedDeliveryDate();
            Shipment activeShipment = shipments
                    .findFirstByPurchaseOrderIdAndStatusInOrderByExpectedArrivalDateAsc(
                            item.getPurchaseOrder().getId(), ACTIVE_SHIPMENT_STATUSES)
                    .orElse(null);
            if (activeShipment != null && activeShipment.getExpectedArrivalDate() != null) {
                arrivalDate = activeShipment.getExpectedArrivalDate();
            }
            if (arrivalDate == null || arrivalDate.isBefore(today.plusDays(1)) || arrivalDate.isAfter(horizonEnd)) {
                continue;
            }
            result.merge(arrivalDate, item.getQuantity(), BigDecimal::add);
        }
        return result;
    }

    private static BigDecimal sumIncoming(Map<LocalDate, BigDecimal> incoming, LocalDate from, LocalDate to) {
        return incoming.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(from) && !entry.getKey().isAfter(to))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static ImpactLevel classifyRisk(
            LocalDate today,
            LocalDate stockoutDate,
            BigDecimal projected28,
            BigDecimal lowStockThreshold) {
        if (stockoutDate != null) {
            long daysUntilStockout = java.time.temporal.ChronoUnit.DAYS.between(today, stockoutDate);
            return daysUntilStockout <= 7 ? ImpactLevel.CRITICAL : ImpactLevel.HIGH;
        }
        if (projected28.compareTo(lowStockThreshold) <= 0) {
            return ImpactLevel.MEDIUM;
        }
        return ImpactLevel.LOW;
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
