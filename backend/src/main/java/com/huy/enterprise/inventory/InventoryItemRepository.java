package com.huy.enterprise.inventory;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem,UUID>{
 boolean existsByWarehouseIdAndProductId(UUID warehouseId,UUID productId);
 @Query("select i from InventoryItem i where i.quantity <= i.lowStockThreshold") List<InventoryItem> findLowStockItems();
 @Query("select count(i) from InventoryItem i where i.quantity <= i.lowStockThreshold") long countLowStockItems();
}
