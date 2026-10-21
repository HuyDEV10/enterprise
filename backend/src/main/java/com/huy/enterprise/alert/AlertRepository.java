package com.huy.enterprise.alert;

import com.huy.enterprise.common.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    long countByStatusIn(Collection<AlertStatus> statuses);

    Optional<Alert> findFirstByRiskEventIdAndStatusIn(
            UUID riskEventId,
            Collection<AlertStatus> statuses);

    List<Alert> findByRiskEventIdAndStatusIn(
            UUID riskEventId,
            Collection<AlertStatus> statuses);
}
