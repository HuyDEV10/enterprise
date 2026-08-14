package com.huy.enterprise.product;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/products") @RequiredArgsConstructor
public class ProductController {
    private final ProductService service;
    @GetMapping public List<ProductResponse> all(){return service.findAll();}
    @GetMapping("/{id}") public ProductResponse one(@PathVariable UUID id){return service.findById(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ProductResponse create(@Valid @RequestBody CreateProductRequest r){return service.create(r);}
    @PutMapping("/{id}") public ProductResponse update(@PathVariable UUID id,@Valid @RequestBody UpdateProductRequest r){return service.update(id,r);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id){service.deactivate(id);}
}
