package com.huy.enterprise.company;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface DepartmentRepository extends JpaRepository<Department,UUID>{boolean existsByCode(String code);}
