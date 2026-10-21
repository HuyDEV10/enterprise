package com.huy.enterprise.company;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface CompanyRepository extends JpaRepository<Company,UUID>{boolean existsByTaxCode(String taxCode);}
