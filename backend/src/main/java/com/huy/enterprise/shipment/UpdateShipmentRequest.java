package com.huy.enterprise.shipment;
import java.time.LocalDate; import java.util.UUID; import jakarta.validation.constraints.NotNull;
public record UpdateShipmentRequest(@NotNull UUID purchaseOrderId,String carrierName,LocalDate departureDate,LocalDate expectedArrivalDate,LocalDate actualArrivalDate,String currentLocation,String notes){}
