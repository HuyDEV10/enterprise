public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    @Query("SELECT COUNT(i) FROM InventoryItem i WHERE i.quantity < i.lowStockThreshold")
    long countLowStockItems();

}
