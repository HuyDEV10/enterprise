package com.huy.enterprise.company;
import java.util.UUID; import com.huy.enterprise.common.enums.RecordStatus;
public record DepartmentResponse(UUID id,UUID companyId,String companyName,String name,String code,String description,RecordStatus status){static DepartmentResponse from(Department d){return new DepartmentResponse(d.getId(),d.getCompany().getId(),d.getCompany().getName(),d.getName(),d.getCode(),d.getDescription(),d.getStatus());}}
