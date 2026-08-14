package com.huy.enterprise.company;
import java.util.UUID; import com.huy.enterprise.common.enums.RecordStatus;
public record CompanyResponse(UUID id,String name,String taxCode,String email,String phone,String address,RecordStatus status){static CompanyResponse from(Company c){return new CompanyResponse(c.getId(),c.getName(),c.getTaxCode(),c.getEmail(),c.getPhone(),c.getAddress(),c.getStatus());}}
