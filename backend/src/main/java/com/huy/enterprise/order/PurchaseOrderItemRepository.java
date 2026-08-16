package com.huy.enterprise.order;

import com.huy.enterprise.common.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, UUID> {
    @Query("select i from PurchaseOrderItem i join fetch i.purchaseOrder po " +
            "where i.product.id = :productId and po.status in :statuses")
    List<PurchaseOrderItem> findIncomingItems(
            @Param("productId") UUID productId,
            @Param("statuses") Collection<PurchaseOrderStatus> statuses);
}
