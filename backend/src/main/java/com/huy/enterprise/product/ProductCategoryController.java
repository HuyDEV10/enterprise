package com.huy.enterprise.product;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/product-categories") @RequiredArgsConstructor
public class ProductCategoryController {
 private final ProductCategoryService service;
 @GetMapping public List<ProductCategoryResponse> all(){return service.all();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public ProductCategoryResponse create(@Valid @RequestBody ProductCategoryRequest r){return service.create(r);}
 @PutMapping("/{id}") public ProductCategoryResponse update(@PathVariable UUID id,@Valid @RequestBody ProductCategoryRequest r){return service.update(id,r);}
}
