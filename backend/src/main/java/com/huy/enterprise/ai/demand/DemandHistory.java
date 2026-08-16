package com.huy.enterprise.ai.demand;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.huy.enterprise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
        name = "demand_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_demand_history_mapping_date",
                columnNames = {"mapping_id", "demand_date"}))
public class DemandHistory extends BaseEntity {
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mapping_id", nullable = false)
    private DemandSeriesMapping mapping;

    @Column(name = "demand_date", nullable = false)
    private LocalDate demandDate;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "sell_price", precision = 15, scale = 4)
    private BigDecimal sellPrice;

    @Column(name = "source_day_key", length = 20)
    private String sourceDayKey;
}
