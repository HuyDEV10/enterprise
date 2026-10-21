package com.huy.enterprise.inventory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.huy.enterprise.common.BaseEntity;
import com.huy.enterprise.product.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "inventory_items")
public class InventoryItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;
    @Column(name = "low_stock_threshold", nullable = false, precision = 15, scale = 2)
    private BigDecimal lowStockThreshold = BigDecimal.ZERO;
    @Column(name = "last_updated_at", nullable = false)
    private OffsetDateTime lastUpdated = OffsetDateTime.now();
}
