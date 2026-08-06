package com.example.enterprise.supplier;

import com.example.enterprise.common.enums.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    long countByRiskLevel(RiskLevel riskLevel);
}