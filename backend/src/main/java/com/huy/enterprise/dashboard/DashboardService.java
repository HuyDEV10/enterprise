package com.huy.enterprise.dashboard;

import java.util.List;

import com.huy.enterprise.alert.AlertRepository;
import com.huy.enterprise.common.enums.AlertStatus;
import com.huy.enterprise.common.enums.PurchaseOrderStatus;
import com.huy.enterprise.common.enums.RiskEventStatus;
import com.huy.enterprise.common.enums.RiskLevel;
import com.huy.enterprise.inventory.InventoryItemRepository;
import com.huy.enterprise.order.PurchaseOrderRepository;
import com.huy.enterprise.risk.RiskEventRepository;
import com.huy.enterprise.supplier.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final RiskEventRepository riskEventRepository;
    private final AlertRepository alertRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public DashboardSummaryResponse getSummary() {
        return new DashboardSummaryResponse(
                supplierRepository.count(),
                supplierRepository.countByRiskLevel(RiskLevel.HIGH),
                purchaseOrderRepository.count(),
                purchaseOrderRepository.countByStatus(PurchaseOrderStatus.DELAYED),
                riskEventRepository.countByStatus(RiskEventStatus.OPEN),
                alertRepository.countByStatusIn(List.of(AlertStatus.NEW, AlertStatus.READ)),
                inventoryItemRepository.countLowStockItems());
    }
}
