package com.huy.enterprise.demo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoDatasetTest {
    private final String password = "Demo-fixture-test-Only!";
    private final String database = "enterprise_seed_test_" + UUID.randomUUID().toString().replace("-", "") + "_demo";
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private DemoDataset dataset;
    private String adminUrl, user, dbPassword;

    @BeforeAll
    void prepareIsolatedDatabase() throws Exception {
        String port = System.getenv().getOrDefault("DB_PORT", "5433");
        user = System.getenv().getOrDefault("DB_USER", "postgres");
        dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "postgres");
        adminUrl = "jdbc:postgresql://localhost:" + port + "/postgres";
        try (var connection = DriverManager.getConnection(adminUrl, user, dbPassword); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        var source = new DriverManagerDataSource("jdbc:postgresql://localhost:" + port + "/" + database, user, dbPassword);
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        dataset = new DemoDataset(jdbc, new BCryptPasswordEncoder(4), manager, new MockEnvironment());
    }

    @AfterAll
    void removeOnlyOwnedTestDatabase() throws Exception {
        if (adminUrl == null) return;
        try (var connection = DriverManager.getConnection(adminUrl, user, dbPassword); var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }

    private void isolated(Consumer<DemoDataset> check) {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            try { check.accept(dataset); } finally { status.setRollbackOnly(); }
        });
    }

    @Test
    void seedsModerateCoherentFixturesAndPreservesUserEditsOnRepeat() {
        isolated(data -> {
            assertThat(data.initialize(password)).isTrue();
            Map<String, Integer> counts = Map.of("users", 24, "suppliers", 30, "products", 40, "warehouses", 4,
                    "inventory_items", 160, "purchase_orders", 60, "purchase_order_items", 180,
                    "shipments", 50, "demand_history", 1080, "external_event_impacts", 36);
            counts.forEach((table, expected) -> assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isEqualTo(expected));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_orders p WHERE p.total_amount <> (SELECT sum(i.line_total) FROM purchase_order_items i WHERE i.purchase_order_id=p.id)", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM shipments s JOIN purchase_orders p ON p.id=s.purchase_order_id WHERE s.expected_arrival_date < p.order_date OR s.departure_date < p.order_date OR (s.status <> 'ARRIVED' AND s.actual_arrival_date IS NOT NULL)", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM (SELECT mapping_id FROM demand_history GROUP BY mapping_id HAVING count(*)=90 AND max(demand_date)-min(demand_date)=89) x", Integer.class)).isEqualTo(12);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM demand_series_mappings WHERE source_dataset='DEMO_SYNTHETIC'", Integer.class)).isEqualTo(12);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM external_events WHERE sentiment_model_version IS NOT NULL OR sentiment_confidence IS NOT NULL", Integer.class)).isZero();
            String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username='demo_admin'", String.class);
            assertThat(new BCryptPasswordEncoder().matches(password, hash)).isTrue();
            jdbc.update("UPDATE inventory_items SET quantity=12345 WHERE id=?", DemoDataset.id("inventory", 0));
            assertThat(data.initialize("a-different-password-123")).isFalse();
            assertThat(jdbc.queryForObject("SELECT quantity::int FROM inventory_items WHERE id=?", Integer.class, DemoDataset.id("inventory", 0))).isEqualTo(12345);
            assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE username='demo_admin'", String.class)).isEqualTo(hash);
        });
    }

    @Test
    void rejectsNonemptyDatabaseWithoutTouchingExistingData() {
        isolated(data -> {
            jdbc.update("INSERT INTO warehouses (warehouse_code,name) VALUES ('EXISTING','User data')");
            assertThatThrownBy(() -> data.initialize(password)).isInstanceOf(IllegalStateException.class).hasMessageContaining("empty database");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM warehouses", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
        });
    }

    @Test
    void rejectsMissingPasswordWithoutPartiallySeeding() {
        isolated(data -> {
            assertThatThrownBy(() -> data.initialize("")).isInstanceOf(IllegalStateException.class).hasMessageContaining("DEMO_PASSWORD");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
        });
    }

    @Test
    void refusesNormalDatabaseAndProductionProfile() {
        var template = mock(JdbcTemplate.class);
        var environment = new MockEnvironment();
        when(template.queryForObject("SELECT current_database()", String.class)).thenReturn("enterprise_risk_db");
        var guarded = new DemoDataset(template, new BCryptPasswordEncoder(4), manager, environment);
        assertThatThrownBy(() -> guarded.initialize(password)).hasMessageContaining("ending in _demo");
        verify(template, never()).execute(anyString());
        environment.setActiveProfiles("production", "demo");
        assertThatThrownBy(() -> guarded.initialize(password)).hasMessageContaining("production profile");
    }
}
