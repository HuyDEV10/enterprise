package com.huy.enterprise.inventory;
import java.util.UUID;
import com.huy.enterprise.common.enums.RecordStatus;
public record WarehouseResponse(UUID id,String warehouseCode,String name,String address,RecordStatus status){static WarehouseResponse from(Warehouse w){return new WarehouseResponse(w.getId(),w.getWarehouseCode(),w.getName(),w.getAddress(),w.getStatus());}}
