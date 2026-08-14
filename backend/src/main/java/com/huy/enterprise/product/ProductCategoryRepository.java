package com.huy.enterprise.product;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    boolean existsByCode(String code);
}
