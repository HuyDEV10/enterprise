package com.huy.enterprise.product;

import com.huy.enterprise.common.ResourceNotFoundException;
import com.huy.enterprise.common.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    public Product create(CreateProductRequest request) {
        Product p = new Product();
        if (request.categoryId() != null)
            p.setCategory(productCategoryRepository.findById(request.categoryId()).orElseThrow(
                    () -> new ResourceNotFoundException("Product category not found: " + request.categoryId())));
        p.setProductCode(request.productCode());
        p.setName(request.name());
        p.setUnit(request.unit());
        p.setReferencePrice(request.referencePrice());
        p.setDescription(request.description());
        p.setStatus(request.status() == null ? RecordStatus.ACTIVE : request.status());
        return productRepository.save(p);
    }
}