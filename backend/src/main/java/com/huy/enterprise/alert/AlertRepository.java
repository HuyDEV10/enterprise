package com.huy.enterprise.alert;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    long countByStatusIn(Collection<AlertStatus> statuses);
}
