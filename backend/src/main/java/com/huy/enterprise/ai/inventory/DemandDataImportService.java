package com.huy.enterprise.ai.inventory;

import com.huy.enterprise.ai.client.AiServiceClient;
import com.huy.enterprise.ai.demand.DemandHistory;
import com.huy.enterprise.ai.demand.DemandHistoryRepository;
import com.huy.enterprise.ai.demand.DemandSeriesMapping;
import com.huy.enterprise.ai.demand.DemandSeriesMappingRepository;
import com.huy.enterprise.common.ConflictException;
import com.huy.enterprise.common.ResourceNotFoundException;
import com.huy.enterprise.product.Product;
import com.huy.enterprise.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DemandDataImportService {
    private static final String SOURCE_DATASET = "M5";

    private final ProductRepository products;
    private final DemandSeriesMappingRepository mappings;
    private final DemandHistoryRepository demandHistory;
    private final AiServiceClient aiClient;

    @Transactional
    public DemandImportResponse importM5(UUID productId, String itemId, String storeId) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        AiServiceClient.M5SeriesResponse source = aiClient.loadM5Series(itemId, storeId);
        DemandSeriesMapping mapping = mappings
                .findBySourceDatasetAndSourceItemIdAndSourceLocationId(SOURCE_DATASET, source.itemId(), source.storeId())
                .orElse(null);

        if (mapping != null && !mapping.getProduct().getId().equals(productId)) {
            throw new ConflictException("M5 series " + source.itemId() + "/" + source.storeId()
                    + " is already mapped to another product");
        }

        if (mapping == null) {
            mapping = new DemandSeriesMapping();
            mapping.setProduct(product);
            mapping.setSourceDataset(SOURCE_DATASET);
            mapping.setSourceItemId(source.itemId());
            mapping.setSourceLocationId(source.storeId());
        }
        mapping.setCategoryCode(source.categoryId());
        mapping.setDepartmentCode(source.departmentId());
        mapping.setActive(true);
        mapping = mappings.save(mapping);

        demandHistory.deleteByMappingId(mapping.getId());
        List<DemandHistory> rows = new ArrayList<>(source.history().size());
        for (AiServiceClient.M5DemandPoint point : source.history()) {
            DemandHistory row = new DemandHistory();
            row.setMapping(mapping);
            row.setDemandDate(point.date());
            row.setQuantity(BigDecimal.valueOf(point.quantity()));
            row.setSellPrice(point.sellPrice() == null ? null : BigDecimal.valueOf(point.sellPrice()));
            row.setSourceDayKey(point.sourceDayKey());
            rows.add(row);
        }
        demandHistory.saveAll(rows);

        return new DemandImportResponse(
                mapping.getId(),
                product.getId(),
                product.getProductCode(),
                SOURCE_DATASET,
                source.itemId(),
                source.storeId(),
                source.departmentId(),
                rows.size(),
                rows.getFirst().getDemandDate(),
                rows.getLast().getDemandDate());
    }
}
