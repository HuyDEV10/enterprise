package com.huy.enterprise.supplier;

import com.huy.enterprise.common.enums.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    long countByRiskLevel(RiskLevel riskLevel);
    Optional<Supplier> findBySupplierCode(String supplierCode);
    boolean existsBySupplierCode(String supplierCode);
    List<Supplier> findByRiskLevel(RiskLevel riskLevel);
}
