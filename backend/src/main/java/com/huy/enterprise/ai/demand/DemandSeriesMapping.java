package com.huy.enterprise.ai.demand;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.huy.enterprise.common.BaseEntity;
import com.huy.enterprise.inventory.Warehouse;
import com.huy.enterprise.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "demand_series_mappings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_demand_series_source",
                columnNames = {"source_dataset", "source_item_id", "source_location_id"}))
public class DemandSeriesMapping extends BaseEntity {
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(name = "source_dataset", nullable = false, length = 50)
    private String sourceDataset;

    @Column(name = "source_item_id", nullable = false, length = 100)
    private String sourceItemId;

    @Column(name = "source_location_id", nullable = false, length = 100)
    private String sourceLocationId;

    @Column(name = "category_code", length = 100)
    private String categoryCode;

    @Column(name = "department_code", length = 100)
    private String departmentCode;

    @Column(nullable = false)
    private boolean active = true;
}
