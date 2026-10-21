package com.huy.enterprise.supplier;

import com.huy.enterprise.common.enums.RiskLevel;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {
    private final SupplierService service;
    @GetMapping public List<SupplierResponse> findAll(@RequestParam(required = false) RiskLevel riskLevel) { return service.findAll(riskLevel); }
    @GetMapping("/{id}") public SupplierResponse findById(@PathVariable UUID id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public SupplierResponse create(@Valid @RequestBody CreateSupplierRequest r) { return service.create(r); }
    @PutMapping("/{id}") public SupplierResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSupplierRequest r) { return service.update(id, r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.deactivate(id); }

    @GetMapping("/page")
    public com.huy.enterprise.common.PageResponse<SupplierResponse> page(
            @RequestParam(required = false) RiskLevel riskLevel,
            @org.springframework.data.web.PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            org.springframework.data.domain.Pageable pageable) { return service.page(riskLevel, pageable); }
}
