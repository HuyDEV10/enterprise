package com.huy.enterprise.order;

import com.huy.enterprise.common.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    long countByStatus(PurchaseOrderStatus status);
    Optional<PurchaseOrder> findByOrderCode(String orderCode);
    boolean existsByOrderCode(String orderCode);

    @Query("select o from PurchaseOrder o join fetch o.supplier " +
            "where o.expectedDeliveryDate is not null " +
            "and o.expectedDeliveryDate < :today " +
            "and o.status in :statuses")
    List<PurchaseOrder> findOverdueOrders(
            @Param("today") LocalDate today,
            @Param("statuses") Collection<PurchaseOrderStatus> statuses);
}
