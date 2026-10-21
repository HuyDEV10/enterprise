package com.huy.enterprise.alert;

import java.time.OffsetDateTime;
import com.huy.enterprise.common.BaseEntity;
import com.huy.enterprise.common.enums.AlertPriority;
import com.huy.enterprise.common.enums.AlertStatus;
import com.huy.enterprise.risk.RiskEvent;
import com.huy.enterprise.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "alerts")
public class Alert extends BaseEntity {
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String message;
    @Column(name = "alert_type", nullable = false, length = 100)
    private String alertType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AlertPriority priority = AlertPriority.MEDIUM;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AlertStatus status = AlertStatus.NEW;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id")
    private User recipientUser;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "risk_event_id")
    private RiskEvent riskEvent;
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}
