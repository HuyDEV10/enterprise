public record DashboardSummaryReponse(
        long totalSuppliers,
        long highRiskSuppliers,
        long totalPurchaseOrders,
        long delayedPurchaseOrders,
        long openRiskEvents,
        long unresolvedAlerts,
        long lowStockItems) {
}
