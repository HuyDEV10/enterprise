package com.huy.enterprise.ai.external;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.huy.enterprise.common.BaseEntity;
import com.huy.enterprise.common.enums.ImpactLevel;
import com.huy.enterprise.supplier.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "external_event_impacts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_external_event_supplier_impact",
                columnNames = {"external_event_id", "supplier_id"}))
public class ExternalEventImpact extends BaseEntity {
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "external_event_id", nullable = false)
    private ExternalEvent externalEvent;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "impact_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal impactScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", nullable = false, length = 30)
    private ImpactLevel impactLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_type", nullable = false, length = 30)
    private ExternalEventSignal impactType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;
}
