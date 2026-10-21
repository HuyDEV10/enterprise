package com.huy.enterprise.company;
import java.util.UUID; import com.huy.enterprise.common.enums.RecordStatus;
public record EmployeeResponse(UUID id,UUID companyId,UUID departmentId,UUID userId,String employeeCode,String fullName,String email,String phone,String jobTitle,RecordStatus status){static EmployeeResponse from(Employee e){return new EmployeeResponse(e.getId(),e.getCompany().getId(),e.getDepartment()==null?null:e.getDepartment().getId(),e.getUser()==null?null:e.getUser().getId(),e.getEmployeeCode(),e.getFullName(),e.getEmail(),e.getPhone(),e.getJobTitle(),e.getStatus());}}
