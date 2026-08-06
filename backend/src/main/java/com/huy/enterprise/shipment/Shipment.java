package com.example.enterprise.shipment;

import com.example.enterprise.common.BaseEntity;
import com.example.enterprise.common.enums.ShipmentStatus;
import com.example.enterprise.order.PurchaseOrder;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "shipments")
public class Shipment extends BaseEntity {
    @Column(name = "shipment_code", nullable = false, unique = true, length = 100)
    private String shipmentCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;
    @Column(name = "carrier_name")
    private String carrierName;
    @Column(name = "departure_date")
    private LocalDate departureDate;
    @Column(name = "expected_arrival_date")
    private LocalDate expectedArrivalDate;
    @Column(name = "actual_arrival_date")
    private LocalDate actualArrivalDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status = ShipmentStatus.PENDING;
    @Column(name = "current_location")
    private String currentLocation;
    private String notes;
}