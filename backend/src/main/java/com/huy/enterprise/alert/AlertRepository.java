package com.huy.enterprise.alert;

import java.util.Collection;
import java.util.UUID;
import com.huy.enterprise.common.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    long countByStatusIn(Collection<AlertStatus> statuses);
}
