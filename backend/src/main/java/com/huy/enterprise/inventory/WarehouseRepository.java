package com.huy.enterprise.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WarehouseRepository extends JpaRepository<Warehouse,UUID>{boolean existsByWarehouseCode(String warehouseCode);}
