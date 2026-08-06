package com.example.enterprise.product;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @GetMapping
    public List<Product> findAll() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public Product findById(@PathVariable UUID id) {
        return productService.findById(id);
    }

    @PostMapping
    public Product create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }
}