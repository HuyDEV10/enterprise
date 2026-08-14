package com.huy.enterprise.shipment;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ShipmentRepository extends JpaRepository<Shipment,UUID>{boolean existsByShipmentCode(String shipmentCode);}
