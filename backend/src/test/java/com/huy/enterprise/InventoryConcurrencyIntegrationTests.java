package com.huy.enterprise;

import com.huy.enterprise.inventory.*;
import com.huy.enterprise.product.*;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class InventoryConcurrencyIntegrationTests {
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ProductRepository products;
    @Autowired WarehouseRepository warehouses;
    @Autowired InventoryItemRepository inventory;

    @Test
    void concurrentStockWritersCannotSilentlyOverwriteEachOther() {
        var tx = new TransactionTemplate(transactionManager);
        UUID[] ids = tx.execute(status -> {
            Product product = new Product();
            product.setProductCode("CONCURRENT-" + UUID.randomUUID());
            product.setName("Concurrency test");
            product.setUnit("pcs");
            products.save(product);
            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("CONCURRENT-" + UUID.randomUUID());
            warehouse.setName("Concurrency test");
            warehouses.save(warehouse);
            InventoryItem item = new InventoryItem();
            item.setWarehouse(warehouse);
            item.setProduct(product);
            item.setQuantity(BigDecimal.TEN);
            item.setLowStockThreshold(BigDecimal.ZERO);
            item.setLastUpdated(OffsetDateTime.now());
            inventory.saveAndFlush(item);
            return new UUID[]{item.getId(), product.getId(), warehouse.getId()};
        });
        var first = factory.createEntityManager();
        var second = factory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            var left = first.find(InventoryItem.class, ids[0]);
            var right = second.find(InventoryItem.class, ids[0]);
            left.setQuantity(new BigDecimal("8"));
            first.getTransaction().commit();
            right.setQuantity(new BigDecimal("7"));
            assertThatThrownBy(() -> second.getTransaction().commit()).isInstanceOf(RollbackException.class)
                    .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
            BigDecimal persisted = tx.execute(status -> inventory.findById(ids[0]).orElseThrow().getQuantity());
            assertThat(persisted).isEqualByComparingTo("8");
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close();
            second.close();
            // Remove only this test's committed fixtures, preserving development data.
            tx.executeWithoutResult(status -> {
                inventory.deleteById(ids[0]);
                inventory.flush();
                products.deleteById(ids[1]);
                warehouses.deleteById(ids[2]);
            });
        }
    }
}
