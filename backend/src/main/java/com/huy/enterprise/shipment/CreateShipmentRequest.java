package com.huy.enterprise.shipment;
import java.time.LocalDate; import java.util.UUID; import com.huy.enterprise.common.enums.ShipmentStatus; import jakarta.validation.constraints.*;
public record CreateShipmentRequest(@NotBlank String shipmentCode,@NotNull UUID purchaseOrderId,String carrierName,LocalDate departureDate,LocalDate expectedArrivalDate,LocalDate actualArrivalDate,ShipmentStatus status,String currentLocation,String notes){}
