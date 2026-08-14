package com.huy.enterprise.inventory;
import com.huy.enterprise.common.enums.RecordStatus;
import jakarta.validation.constraints.NotBlank;
public record WarehouseRequest(@NotBlank String warehouseCode,@NotBlank String name,String address,RecordStatus status){}
