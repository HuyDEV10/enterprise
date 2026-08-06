import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

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
                supplierRepository.countByriskLevel(RiskLevel.HIGH),
                purchaseOrderRepository.count(),
                purchaseOrderRepository.countByStatus(PurchaseOrderStatus.DELAYED),
                riskEventRepository.countbySeverity(RiskSeverity.OPEN),
                alertRepository.countbyStatus(List.of(AlertStatus.NEW, AlertStatus.READ)),
                inventoryItemRepository.countLowStockItems());
    }
}
