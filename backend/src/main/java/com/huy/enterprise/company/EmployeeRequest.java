package com.huy.enterprise.company;
import java.util.UUID; import com.huy.enterprise.common.enums.RecordStatus; import jakarta.validation.constraints.*;
public record EmployeeRequest(@NotNull UUID companyId,UUID departmentId,UUID userId,@NotBlank String employeeCode,@NotBlank String fullName,@Email String email,String phone,String jobTitle,RecordStatus status){}
