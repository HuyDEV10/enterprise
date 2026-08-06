package com.huy.enterprise.order;

import java.util.UUID;

import com.huy.enterprise.common.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    long countByStatus(PurchaseOrderStatus status);
}
