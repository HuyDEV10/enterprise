package com.huy.enterprise.product;

import com.huy.enterprise.common.BaseEntity;
import com.huy.enterprise.common.enums.RecordStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "products")
public class Product extends BaseEntity {
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;
    @Column(name = "product_code", nullable = false, unique = true, length = 100)
    private String productCode;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, length = 50)
    private String unit;
    @Column(name = "reference_price", precision = 15, scale = 2)
    private BigDecimal referencePrice;
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecordStatus status = RecordStatus.ACTIVE;
}
