package com.huy.enterprise.company;
import com.huy.enterprise.common.enums.RecordStatus; import jakarta.validation.constraints.*;
public record CompanyRequest(@NotBlank String name,String taxCode,@Email String email,String phone,String address,RecordStatus status){}
