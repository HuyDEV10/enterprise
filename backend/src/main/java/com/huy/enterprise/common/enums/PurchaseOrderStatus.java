package com.huy.enterprise.common.enums;

public enum PurchaseOrderStatus {
    DRAFT,
    ORDERED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED,
    DELAYED;

    public boolean canTransitionTo(PurchaseOrderStatus next) {
        if (this == next) return true;
        return switch (this) {
            case DRAFT -> next == ORDERED || next == CANCELLED;
            case ORDERED -> next == IN_TRANSIT || next == DELAYED || next == CANCELLED;
            case IN_TRANSIT -> next == DELIVERED || next == DELAYED || next == CANCELLED;
            case DELAYED -> next == ORDERED || next == IN_TRANSIT || next == DELIVERED || next == CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
