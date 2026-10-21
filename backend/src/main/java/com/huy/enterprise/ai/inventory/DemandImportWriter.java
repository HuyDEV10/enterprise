package com.huy.enterprise.ai.inventory;

import com.huy.enterprise.ai.client.AiServiceClient.M5SeriesResponse;
import com.huy.enterprise.ai.demand.DemandSeriesMapping;
import com.huy.enterprise.ai.demand.DemandSeriesMappingRepository;
import com.huy.enterprise.audit.AuditService;
import com.huy.enterprise.common.ConflictException;
import com.huy.enterprise.common.ResourceNotFoundException;
import com.huy.enterprise.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DemandImportWriter {
    private final ProductRepository products;
    private final DemandSeriesMappingRepository mappings;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    @Transactional
    public DemandImportResponse save(UUID productId, M5SeriesResponse source) {
        // Serialize mapping changes for a product, including across application instances.
        var product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        var mapping = mappings.findBySourceDatasetAndSourceItemIdAndSourceLocationId("M5", source.itemId(), source.storeId())
                .orElse(null);
        if (mapping != null && !mapping.getProduct().getId().equals(productId)) {
            throw new ConflictException("This M5 series is already mapped to another product");
        }
        if (mapping == null) {
            mapping = new DemandSeriesMapping();
            mapping.setProduct(product);
            mapping.setSourceDataset("M5");
            mapping.setSourceItemId(source.itemId());
            mapping.setSourceLocationId(source.storeId());
            mapping.setActive(false);
            mapping = mappings.saveAndFlush(mapping);
        }
        mappings.deactivateOtherMappings(productId, mapping.getId());
        mapping.setCategoryCode(source.categoryId());
        mapping.setDepartmentCode(source.departmentId());
        mapping.setActive(true);
        mappings.saveAndFlush(mapping);

        UUID mappingId = mapping.getId();
        jdbc.batchUpdate("""
                INSERT INTO demand_history (mapping_id, demand_date, quantity, sell_price, source_day_key)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (mapping_id, demand_date) DO UPDATE
                SET quantity = EXCLUDED.quantity, sell_price = EXCLUDED.sell_price,
                    source_day_key = EXCLUDED.source_day_key, updated_at = now(), version = demand_history.version + 1
                """, source.history(), 500, (statement, point) -> {
            statement.setObject(1, mappingId);
            statement.setObject(2, point.date());
            statement.setBigDecimal(3, BigDecimal.valueOf(point.quantity()));
            statement.setBigDecimal(4, point.sellPrice() == null ? null : BigDecimal.valueOf(point.sellPrice()));
            statement.setString(5, point.sourceDayKey());
        });
        var from = source.history().getFirst().date();
        var to = source.history().getLast().date();
        jdbc.update("DELETE FROM demand_history WHERE mapping_id = ? AND (demand_date < ? OR demand_date > ?)", mappingId, from, to);
        audit.record("DEMAND_IMPORTED", "PRODUCT", productId,
                "M5 mapping=" + mappingId + "; rows=" + source.history().size() + "; range=" + from + ".." + to);
        return new DemandImportResponse(mappingId, productId, product.getProductCode(), "M5", source.itemId(),
                source.storeId(), source.departmentId(), source.history().size(), from, to);
    }
}
