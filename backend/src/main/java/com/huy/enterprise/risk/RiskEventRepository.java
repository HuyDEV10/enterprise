package com.huy.enterprise.risk;

import com.huy.enterprise.common.enums.RiskEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RiskEventRepository extends JpaRepository<RiskEvent, UUID> {
    long countByStatus(RiskEventStatus status);
}