package com.huy.enterprise.product;

import com.huy.enterprise.common.BaseEntity;
import com.huy.enterprise.common.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "product_categories")
public class ProductCategory extends BaseEntity {
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true, length = 100)
    private String code;
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecordStatus status = RecordStatus.ACTIVE;
}