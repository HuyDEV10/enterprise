package com.huy.enterprise.ai.inventory;

import com.huy.enterprise.ai.AiServiceInputException;
import com.huy.enterprise.ai.AiServiceProtocolException;
import com.huy.enterprise.ai.client.AiServiceClient;
import com.huy.enterprise.common.ResourceNotFoundException;
import com.huy.enterprise.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DemandDataImportService {
    private final ProductRepository products;
    private final AiServiceClient aiClient;
    private final DemandImportWriter writer;

    public DemandImportResponse importM5(UUID productId, String itemId, String storeId) {
        if (itemId == null || itemId.isBlank() || itemId.length() > 100
                || storeId == null || storeId.isBlank() || storeId.length() > 100) {
            throw new IllegalArgumentException("M5 itemId and storeId must contain 1 to 100 characters");
        }
        if (!products.existsById(productId)) throw new ResourceNotFoundException("Product not found: " + productId);
        // Network I/O and validation complete before the write transaction starts.
        var source = aiClient.loadM5Series(itemId.trim(), storeId.trim());
        if (source == null || !itemId.trim().equals(source.itemId()) || !storeId.trim().equals(source.storeId())
                || source.history() == null || source.history().size() > 5000) {
            throw new AiServiceProtocolException("M5 service returned an invalid series identity or history");
        }
        if (source.history().size() < 56) {
            throw new AiServiceInputException("INSUFFICIENT_HISTORY", "At least 56 contiguous history days are required");
        }
        var history = new ArrayList<>(source.history());
        if (history.stream().anyMatch(point -> point == null || point.date() == null)) {
            throw new AiServiceInputException("INVALID_HISTORY", "Demand history contains missing dates");
        }
        history.sort(Comparator.comparing(AiServiceClient.M5DemandPoint::date));
        LocalDate previous = null;
        for (var point : history) {
            if (point.quantity() == null || !Double.isFinite(point.quantity()) || point.quantity() < 0
                    || (point.sellPrice() != null && (!Double.isFinite(point.sellPrice()) || point.sellPrice() < 0))
                    || (point.sourceDayKey() != null && point.sourceDayKey().length() > 20)) {
                throw new AiServiceInputException("INVALID_HISTORY", "Demand history contains invalid quantities, prices or source keys");
            }
            if (previous != null && !point.date().equals(previous.plusDays(1))) {
                throw new AiServiceInputException("INVALID_HISTORY", "Demand history must contain unique, contiguous dates");
            }
            previous = point.date();
        }
        var validated = new AiServiceClient.M5SeriesResponse(source.seriesId(), source.itemId(), source.storeId(),
                source.departmentId(), source.categoryId(), source.stateId(), history);
        return writer.save(productId, validated);
    }
}
