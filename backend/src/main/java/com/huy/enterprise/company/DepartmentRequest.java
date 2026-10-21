package com.huy.enterprise.company;
import java.util.UUID; import com.huy.enterprise.common.enums.RecordStatus; import jakarta.validation.constraints.*;
public record DepartmentRequest(@NotNull UUID companyId,@NotBlank String name,@NotBlank String code,String description,RecordStatus status){}
