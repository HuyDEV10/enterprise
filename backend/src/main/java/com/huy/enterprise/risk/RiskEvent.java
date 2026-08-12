package com.huy.enterprise.risk;

import com.huy.enterprise.common.BaseEntity;
import com.huy.enterprise.common.enums.*;
import com.huy.enterprise.order.PurchaseOrder;
import com.huy.enterprise.product.Product;
import com.huy.enterprise.shipment.Shipment;
import com.huy.enterprise.supplier.Supplier;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "risk_events")
public class RiskEvent extends BaseEntity {
    @Column(nullable = false)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_type", nullable = false, length = 50)
    private RiskType riskType;
    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", nullable = false, length = 30)
    private ImpactLevel impactLevel;
    private String description;
    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RiskEventStatus status = RiskEventStatus.OPEN;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;
}