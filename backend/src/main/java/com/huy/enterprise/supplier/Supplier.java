package com.huy.enterprise.supplier;

import com.huy.enterprise.common.BaseEntity;
import com.huy.enterprise.common.enums.RecordStatus;
import com.huy.enterprise.common.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "suppliers")
public class Supplier extends BaseEntity {
    @Column(name = "supplier_code", nullable = false, unique = true, length = 100)
    private String supplierCode;
    @Column(nullable = false)
    private String name;
    private String email;
    private String phone;
    private String address;
    private String country;
    private String region;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecordStatus status = RecordStatus.ACTIVE;
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 30)
    private RiskLevel riskLevel = RiskLevel.LOW;
    private String notes;
}