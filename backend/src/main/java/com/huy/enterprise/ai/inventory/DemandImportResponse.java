package com.huy.enterprise.ai.inventory;

import java.time.LocalDate;
import java.util.UUID;

public record DemandImportResponse(
        UUID mappingId,
        UUID productId,
        String productCode,
        String sourceDataset,
        String sourceItemId,
        String sourceLocationId,
        String departmentCode,
        int historyRows,
        LocalDate dateStart,
        LocalDate dateEnd) {
}
