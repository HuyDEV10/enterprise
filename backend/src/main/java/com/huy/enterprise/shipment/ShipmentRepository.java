package com.huy.enterprise.shipment;

import com.huy.enterprise.common.enums.ShipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    boolean existsByShipmentCode(String shipmentCode);

    @Query("select s from Shipment s " +
            "join fetch s.purchaseOrder po " +
            "join fetch po.supplier " +
            "where s.expectedArrivalDate is not null " +
            "and s.expectedArrivalDate < :today " +
            "and s.status in :statuses")
    List<Shipment> findOverdueShipments(
            @Param("today") LocalDate today,
            @Param("statuses") Collection<ShipmentStatus> statuses);

    Optional<Shipment> findFirstByPurchaseOrderIdAndStatusInOrderByExpectedArrivalDateAsc(
            UUID purchaseOrderId,
            Collection<ShipmentStatus> statuses);
}
