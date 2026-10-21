package com.huy.enterprise.company;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface EmployeeRepository extends JpaRepository<Employee,UUID>{boolean existsByEmployeeCode(String employeeCode);}
