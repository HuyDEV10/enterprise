package com.huy.enterprise.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    boolean existsByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

    @Query("select i from InventoryItem i where i.quantity <= i.lowStockThreshold")
    List<InventoryItem> findLowStockItems();

    @Query("select count(i) from InventoryItem i where i.quantity <= i.lowStockThreshold")
    long countLowStockItems();

    @Query("select coalesce(sum(i.quantity), 0) from InventoryItem i where i.product.id = :productId")
    BigDecimal sumQuantityByProductId(@Param("productId") UUID productId);

    @Query("select coalesce(sum(i.lowStockThreshold), 0) from InventoryItem i where i.product.id = :productId")
    BigDecimal sumLowStockThresholdByProductId(@Param("productId") UUID productId);
}
