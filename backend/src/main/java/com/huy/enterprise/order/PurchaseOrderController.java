package com.huy.enterprise.order;
import java.util.*; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/purchase-orders") @RequiredArgsConstructor public class PurchaseOrderController{private final PurchaseOrderService service;@GetMapping public List<PurchaseOrderResponse> all(){return service.all();}@GetMapping("/{id}") public PurchaseOrderResponse one(@PathVariable UUID id){return service.one(id);}@PostMapping @ResponseStatus(HttpStatus.CREATED) public PurchaseOrderResponse create(@Valid @RequestBody CreatePurchaseOrderRequest r){return service.create(r);}@PutMapping("/{id}") public PurchaseOrderResponse update(@PathVariable UUID id,@Valid @RequestBody UpdatePurchaseOrderRequest r){return service.update(id,r);}@PatchMapping("/{id}/status") public PurchaseOrderResponse status(@PathVariable UUID id,@Valid @RequestBody UpdatePurchaseOrderStatusRequest r){return service.updateStatus(id,r.status());}
    @GetMapping("/page")
    public com.huy.enterprise.common.PageResponse<PurchaseOrderResponse> page(
            @org.springframework.data.web.PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            org.springframework.data.domain.Pageable pageable) { return service.page(pageable); }
}
