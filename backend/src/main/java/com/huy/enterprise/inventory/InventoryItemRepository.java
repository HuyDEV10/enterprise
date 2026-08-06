package com.huy.enterprise.inventory;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    @Query("SELECT COUNT(i) FROM InventoryItem i WHERE i.quantity < i.lowStockThreshold")
    long countLowStockItems();

}
