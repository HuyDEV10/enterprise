package com.huy.enterprise.shipment;
import com.huy.enterprise.common.enums.ShipmentStatus; import jakarta.validation.constraints.NotNull;
public record UpdateShipmentStatusRequest(@NotNull ShipmentStatus status){}
