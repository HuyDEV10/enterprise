package com.huy.enterprise.product;

import com.huy.enterprise.common.*;
import com.huy.enterprise.common.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ProductCategoryService {
    private final ProductCategoryRepository repository;
    public List<ProductCategoryResponse> all(){return repository.findAll().stream().map(ProductCategoryResponse::from).toList();}
    @Transactional public ProductCategoryResponse create(ProductCategoryRequest r){
        if(repository.existsByCode(r.code())) throw new ConflictException("Product category code already exists: "+r.code());
        ProductCategory c=new ProductCategory(); c.setCode(r.code()); apply(c,r); return ProductCategoryResponse.from(repository.save(c));
    }
    @Transactional public ProductCategoryResponse update(UUID id,ProductCategoryRequest r){
        ProductCategory c=repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Product category not found: "+id));
        if(!c.getCode().equals(r.code()) && repository.existsByCode(r.code())) throw new ConflictException("Product category code already exists: "+r.code());
        c.setCode(r.code()); apply(c,r); return ProductCategoryResponse.from(repository.save(c));
    }
    private void apply(ProductCategory c,ProductCategoryRequest r){c.setName(r.name());c.setDescription(r.description());c.setStatus(r.status()==null?RecordStatus.ACTIVE:r.status());}
}
