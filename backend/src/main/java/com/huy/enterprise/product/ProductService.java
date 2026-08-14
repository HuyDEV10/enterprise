package com.huy.enterprise.product;

import com.huy.enterprise.common.*;
import com.huy.enterprise.common.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ProductService {
    private final ProductRepository repository;
    private final ProductCategoryRepository categoryRepository;
    public List<ProductResponse> findAll() { return repository.findAll().stream().map(ProductResponse::from).toList(); }
    public ProductResponse findById(UUID id) { return ProductResponse.from(get(id)); }
    @Transactional public ProductResponse create(CreateProductRequest r) {
        if (repository.existsByProductCode(r.productCode())) throw new ConflictException("Product code already exists: " + r.productCode());
        Product p = new Product(); p.setProductCode(r.productCode()); apply(p, r.categoryId(), r.name(), r.unit(), r.referencePrice(), r.description(), r.status());
        return ProductResponse.from(repository.save(p));
    }
    @Transactional public ProductResponse update(UUID id, UpdateProductRequest r) {
        Product p = get(id); apply(p, r.categoryId(), r.name(), r.unit(), r.referencePrice(), r.description(), r.status()); return ProductResponse.from(repository.save(p));
    }
    @Transactional public void deactivate(UUID id) { Product p=get(id); p.setStatus(RecordStatus.INACTIVE); repository.save(p); }
    private Product get(UUID id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id)); }
    private void apply(Product p, UUID categoryId, String name, String unit, java.math.BigDecimal price, String description, RecordStatus status) {
        p.setCategory(categoryId == null ? null : categoryRepository.findById(categoryId).orElseThrow(() -> new ResourceNotFoundException("Product category not found: " + categoryId)));
        p.setName(name); p.setUnit(unit); p.setReferencePrice(price); p.setDescription(description); p.setStatus(status == null ? RecordStatus.ACTIVE : status);
    }
}
