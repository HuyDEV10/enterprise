package com.example.enterprise.supplier;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {
    private final SupplierService supplierService;

    @GetMapping
    public List<Supplier> findAll() {
        return supplierService.findAll();
    }

    @GetMapping("/{id}")
    public Supplier findById(@PathVariable UUID id) {
        return supplierService.findById(id);
    }

    @PostMapping
    public Supplier create(@Valid @RequestBody CreateSupplierRequest request) {
        return supplierService.create(request);
    }
}