package com.huy.enterprise.inventory;

import com.huy.enterprise.audit.AuditService;
import com.huy.enterprise.common.ConflictException;
import com.huy.enterprise.common.ResourceNotFoundException;
import com.huy.enterprise.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {
    private final InventoryItemRepository repository;
    private final WarehouseRepository warehouses;
    private final ProductRepository products;
    private final AuditService audit;

    public List<InventoryResponse> all() { return repository.findAll().stream().map(InventoryResponse::from).toList(); }
    public InventoryResponse one(UUID id) { return InventoryResponse.from(get(id)); }
    public List<InventoryResponse> lowStock() { return repository.findLowStockItems().stream().map(InventoryResponse::from).toList(); }

    @Transactional
    public InventoryResponse create(InventoryRequest request) {
        if (repository.existsByWarehouseIdAndProductId(request.warehouseId(), request.productId())) {
            throw new ConflictException("Inventory item already exists for warehouse and product");
        }
        InventoryItem item = new InventoryItem();
        apply(item, request);
        item = repository.save(item);
        audit.record("INVENTORY_CREATED", "INVENTORY_ITEM", item.getId(),
                "Quantity=" + item.getQuantity() + "; threshold=" + item.getLowStockThreshold());
        return InventoryResponse.from(item);
    }

    @Transactional
    public InventoryResponse update(UUID id, InventoryRequest request) {
        InventoryItem item = get(id);
        if ((!item.getWarehouse().getId().equals(request.warehouseId()) || !item.getProduct().getId().equals(request.productId()))
                && repository.existsByWarehouseIdAndProductId(request.warehouseId(), request.productId())) {
            throw new ConflictException("Inventory item already exists for warehouse and product");
        }
        BigDecimal previous = item.getQuantity();
        BigDecimal threshold = item.getLowStockThreshold();
        apply(item, request);
        audit.record("INVENTORY_UPDATED", "INVENTORY_ITEM", id, "Quantity: " + previous + " -> " + item.getQuantity()
                + "; threshold: " + threshold + " -> " + item.getLowStockThreshold()
                + "; warehouse=" + request.warehouseId() + "; product=" + request.productId());
        return InventoryResponse.from(repository.save(item));
    }

    private InventoryItem get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + id));
    }

    private void apply(InventoryItem item, InventoryRequest request) {
        item.setWarehouse(warehouses.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + request.warehouseId())));
        item.setProduct(products.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.productId())));
        item.setQuantity(request.quantity());
        item.setLowStockThreshold(request.lowStockThreshold());
        item.setLastUpdated(OffsetDateTime.now());
    }
}
