package com.huy.enterprise.common.enums;

public enum ShipmentStatus {
    PENDING,
    SHIPPING,
    ARRIVED,
    DELAYED,
    CANCELLED;

    public boolean isTerminal() { return this == ARRIVED || this == CANCELLED; }

    public boolean canTransitionTo(ShipmentStatus next) {
        if (this == next) return true;
        return switch (this) {
            case PENDING -> next == SHIPPING || next == DELAYED || next == CANCELLED;
            case SHIPPING -> next == ARRIVED || next == DELAYED || next == CANCELLED;
            case DELAYED -> next == SHIPPING || next == ARRIVED || next == CANCELLED;
            case ARRIVED, CANCELLED -> false;
        };
    }

}
