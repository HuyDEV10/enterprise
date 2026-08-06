package com.huy.enterprise.inventory;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

}
