package com.huy.enterprise.ai.inventory;

import com.huy.enterprise.ai.AiServiceInputException;
import com.huy.enterprise.ai.client.AiServiceClient;
import com.huy.enterprise.ai.demand.*;
import com.huy.enterprise.common.ConflictException;
import com.huy.enterprise.product.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class DemandImportIntegrationTests {
    @MockitoBean AiServiceClient ai;
    @Autowired DemandDataImportService imports;
    @Autowired DemandSeriesMappingRepository mappings;
    @Autowired DemandHistoryRepository history;
    @Autowired ProductRepository products;
    @Autowired EntityManager em;

    @Test
    void repeatedImportUpsertsWithoutChangingRowIdentityOrDuplicatingDates() {
        UUID product = product();
        when(ai.loadM5Series("SERIES-A", "CA_1")).thenReturn(series("SERIES-A", 2));
        var first = imports.importM5(product, "SERIES-A", "CA_1");
        var ids = history.findByMappingIdOrderByDemandDateAsc(first.mappingId()).stream().map(DemandHistory::getId).toList();
        when(ai.loadM5Series("SERIES-A", "CA_1")).thenReturn(series("SERIES-A", 3));
        var second = imports.importM5(product, "SERIES-A", "CA_1");
        em.flush();
        em.clear();
        assertThat(second.mappingId()).isEqualTo(first.mappingId());
        var rows = history.findByMappingIdOrderByDemandDateAsc(first.mappingId());
        assertThat(rows).hasSize(56).allSatisfy(row -> assertThat(row.getQuantity()).isEqualByComparingTo("3"));
        assertThat(rows.stream().map(DemandHistory::getId)).containsExactlyElementsOf(ids);
    }

    @Test
    void switchingSeriesKeepsOneActiveMappingAndPreservesOldHistory() {
        UUID product = product();
        when(ai.loadM5Series("SERIES-A", "CA_1")).thenReturn(series("SERIES-A", 2));
        var first = imports.importM5(product, "SERIES-A", "CA_1");
        when(ai.loadM5Series("SERIES-B", "CA_1")).thenReturn(series("SERIES-B", 4));
        var second = imports.importM5(product, "SERIES-B", "CA_1");
        em.flush();
        em.clear();
        assertThat(mappings.findByProductIdAndActiveTrue(product).orElseThrow().getId()).isEqualTo(second.mappingId());
        assertThat(mappings.findById(first.mappingId()).orElseThrow().isActive()).isFalse();
        assertThat(history.countByMappingId(first.mappingId())).isEqualTo(56);
    }

    @Test
    void aSeriesCannotBeClaimedByAnotherProduct() {
        when(ai.loadM5Series("SERIES-A", "CA_1")).thenReturn(series("SERIES-A", 2));
        imports.importM5(product(), "SERIES-A", "CA_1");
        UUID other = product();
        assertThatThrownBy(() -> imports.importM5(other, "SERIES-A", "CA_1")).isInstanceOf(ConflictException.class);
        assertThat(mappings.findByProductIdAndActiveTrue(other)).isEmpty();
    }

    @Test
    void invalidSourceHistoryIsRejectedBeforeChangingExistingData() {
        UUID product = product();
        var original = series("SERIES-A", 2);
        when(ai.loadM5Series("SERIES-A", "CA_1")).thenReturn(original);
        var imported = imports.importM5(product, "SERIES-A", "CA_1");
        var gap = new java.util.ArrayList<>(original.history());
        var point = gap.getLast();
        gap.set(55, new AiServiceClient.M5DemandPoint(point.date().plusDays(1), 2.0, null, point.sourceDayKey()));
        when(ai.loadM5Series("SERIES-A", "CA_1")).thenReturn(new AiServiceClient.M5SeriesResponse(
                original.seriesId(), original.itemId(), original.storeId(), original.departmentId(), original.categoryId(), original.stateId(), gap));
        assertThatThrownBy(() -> imports.importM5(product, "SERIES-A", "CA_1")).isInstanceOf(AiServiceInputException.class);
        assertThat(history.countByMappingId(imported.mappingId())).isEqualTo(56);
    }

    private UUID product() {
        Product product = new Product();
        product.setProductCode("IMPORT-" + UUID.randomUUID());
        product.setName("Technical import test");
        product.setUnit("pcs");
        return products.saveAndFlush(product).getId();
    }

    private AiServiceClient.M5SeriesResponse series(String item, double quantity) {
        // Technical contract fixtures; never used for ML training or quality evaluation.
        List<AiServiceClient.M5DemandPoint> points = IntStream.range(0, 56).mapToObj(i ->
                new AiServiceClient.M5DemandPoint(LocalDate.of(2026, 1, 1).plusDays(i), quantity, null, "d_" + (i + 1))).toList();
        return new AiServiceClient.M5SeriesResponse(item + "_CA_1", item, "CA_1", "FOODS_3", "FOODS", "CA", points);
    }
}
