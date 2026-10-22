package com.huy.enterprise.demo;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit, isolated UI/integration fixtures. Never training or evaluation data. */
@Component
@Profile("demo")
public class DemoDataset {
    public static final String VERSION = "enterprise-demo-v1";
    private static final UUID MARKER = id("dataset", 1);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String[] TABLES = {"users", "companies", "departments", "employees", "suppliers",
            "product_categories", "products", "warehouses", "inventory_items", "purchase_orders",
            "purchase_order_items", "shipments", "risk_events", "alerts", "audit_logs",
            "demand_series_mappings", "demand_history", "external_events", "external_event_impacts"};
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transaction;
    private final Environment environment;

    public DemoDataset(JdbcTemplate jdbc, PasswordEncoder encoder, PlatformTransactionManager manager,
            Environment environment) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.transaction = new TransactionTemplate(manager);
        this.environment = environment;
    }

    public boolean initialize(String password) {
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException("Demo data cannot run with a production profile.");
        }
        return Boolean.TRUE.equals(transaction.execute(status -> {
            String database = jdbc.queryForObject("SELECT current_database()", String.class);
            if (database == null || !database.endsWith("_demo")) {
                throw new IllegalStateException("Demo seeding requires a dedicated database with a name ending in _demo.");
            }
            jdbc.execute("SELECT pg_advisory_xact_lock(5842391701)");
            if (jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE id = ? AND action = 'DEMO_DATASET_READY'",
                    Integer.class, MARKER) > 0) return false;
            for (String table : TABLES) {
                if (jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class) != 0) {
                    throw new IllegalStateException("Demo initialization requires an empty database. Existing data was not changed.");
                }
            }
            if (password == null || password.length() < 12
                    || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalStateException("Set DEMO_PASSWORD: at least 12 characters and at most 72 UTF-8 bytes.");
            }
            LocalDate today = LocalDate.now(ZONE);
            OffsetDateTime now = OffsetDateTime.now(ZONE);
            seedPeople(password);
            seedCatalog();
            seedInventory(now);
            seedOrders(today, now);
            seedManualRisks(now);
            seedDemand(today);
            seedExternalEvents(today, now);
            insert("audit_logs", row("id", MARKER, "action", "DEMO_DATASET_READY", "entity_type", "DemoDataset",
                    "entity_id", MARKER, "description", VERSION + "; fixture snapshot " + today
                            + "; synthetic UI/integration data, not model training/evaluation"));
            return true;
        }));
    }

    private void seedPeople(String password) {
        String[] roles = {"ADMIN", "MANAGER", "ANALYST", "STAFF"};
        String[] names = {"Quản trị Demo", "Quản lý Demo", "Chuyên viên phân tích Demo", "Nhân viên Demo"};
        UUID company = id("company", 1);
        insert("companies", row("id", company, "name", "[DEMO] Công ty Chuỗi cung ứng An Việt",
                "tax_code", "DEMO-ONLY-001", "email", "office@enterprise.example", "address", "Hà Nội, Việt Nam"));
        String[] departments = {"Điều hành", "Mua hàng", "Kho vận", "Phân tích rủi ro", "Tài chính"};
        for (int i = 0; i < departments.length; i++) {
            insert("departments", row("id", id("department", i), "company_id", company,
                    "code", code("PHONG", i), "name", departments[i], "description", "Phòng ban trong bộ dữ liệu demo"));
        }
        for (int i = 0; i < 24; i++) {
            String username = i < 4 ? "demo_" + roles[i].toLowerCase(Locale.ROOT) : "demo_member_" + String.format("%02d", i - 3);
            String role = i < 4 ? roles[i] : (i % 6 == 0 ? "MANAGER" : "STAFF");
            String name = i < 4 ? names[i] : "Thành viên Demo " + (i - 3);
            insert("users", row("id", id("user", i), "username", username, "email", username + "@enterprise.example",
                    "password_hash", encoder.encode(password), "full_name", name, "status", i == 23 ? "INACTIVE" : "ACTIVE"));
            jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = ?", id("user", i), role);
            insert("employees", row("id", id("employee", i), "company_id", company, "department_id", id("department", i % 5),
                    "user_id", id("user", i), "employee_code", code("NV", i), "full_name", name,
                    "email", username + "@enterprise.example", "job_title", i < 4 ? names[i] : "Chuyên viên vận hành"));
        }
    }

    private void seedCatalog() {
        String[] supplierNames = {"Nông sản An Việt", "Thực phẩm Mekong", "Bao bì Đại Phát", "Logistics Đông Á", "Hàng tiêu dùng Minh Anh",
                "Nguồn hàng Thái Bình", "Cung ứng Sakura", "Thương mại Nam Sơn", "Nguyên liệu Greenfield", "Phân phối Thành Công"};
        String[] countries = {"Việt Nam", "Thái Lan", "Trung Quốc", "Nhật Bản", "Hàn Quốc", "Ấn Độ"};
        String[] regions = {"Đông Nam Á", "Đông Nam Á", "Đông Á", "Đông Á", "Đông Á", "Nam Á"};
        for (int i = 0; i < 30; i++) {
            insert("suppliers", row("id", id("supplier", i), "supplier_code", code("NCC", i),
                    "name", supplierNames[i % 10] + " " + (i / 10 + 1), "country", countries[i % 6], "region", regions[i % 6],
                    "email", "supplier" + (i + 1) + "@enterprise.example", "status", i < 26 ? "ACTIVE" : "INACTIVE",
                    "risk_level", new String[]{"LOW", "MEDIUM", "HIGH"}[i % 3], "notes", "DEMO: đối tác mô phỏng; không phải doanh nghiệp thật"));
        }
        String[] categories = {"Lương thực", "Đồ uống", "Gia vị", "Bao bì", "Gia dụng", "Nguyên liệu"};
        for (int i = 0; i < 6; i++) insert("product_categories", row("id", id("category", i), "code", code("NHOM", i), "name", categories[i]));
        String[] products = {"Gạo thơm Jasmine", "Cà phê Arabica", "Đường tinh luyện", "Thùng carton", "Nước rửa chén", "Bột mì",
                "Gạo lứt", "Trà xanh", "Dầu đậu nành", "Túi giấy", "Khăn giấy", "Sữa bột",
                "Yến mạch", "Nước ép cam", "Muối biển", "Màng bọc", "Ly giấy", "Bột cacao",
                "Mì sợi", "Nước khoáng", "Nước tương", "Hộp giấy", "Găng tay", "Hạt điều",
                "Bún khô", "Cà phê Robusta", "Tiêu đen", "Tem nhãn", "Túi rác", "Đậu xanh",
                "Gạo nếp", "Trà ô long", "Bột ngọt", "Pallet", "Dung dịch vệ sinh", "Hạt hạnh nhân",
                "Ngũ cốc", "Sữa yến mạch", "Quế", "Chai thủy tinh"};
        for (int i = 0; i < 40; i++) {
            insert("products", row("id", id("product", i), "category_id", id("category", i % 6),
                    "product_code", code("SP", i), "name", products[i], "unit", i % 6 == 3 ? "cái" : "thùng",
                    "reference_price", price(i), "status", i < 36 ? "ACTIVE" : "INACTIVE",
                    "description", "Sản phẩm demo " + (i < 12 ? "có 90 ngày lịch sử nhu cầu mô phỏng để thử dự báo" : "cho thử nghiệm vận hành")));
        }
        String[] warehouses = {"Kho Hà Nội", "Kho Đà Nẵng", "Kho TP. Hồ Chí Minh", "Kho Cần Thơ"};
        for (int i = 0; i < 4; i++) insert("warehouses", row("id", id("warehouse", i), "warehouse_code", code("KHO", i),
                "name", warehouses[i], "address", warehouses[i].replace("Kho ", "") + ", Việt Nam"));
    }

    private void seedInventory(OffsetDateTime now) {
        for (int product = 0; product < 40; product++) {
            for (int warehouse = 0; warehouse < 4; warehouse++) {
                int quantity = product == 0 ? 0 : product == 1 ? 2 : product < 4 ? 30 : product < 8 ? 90
                        : product < 12 ? 700 : (product + warehouse) % 7 == 0 ? 15 : 150 + product * 8 + warehouse * 20;
                insert("inventory_items", row("id", id("inventory", product * 4 + warehouse), "warehouse_id", id("warehouse", warehouse),
                        "product_id", id("product", product), "quantity", quantity, "low_stock_threshold", 40,
                        "last_updated_at", now.minusHours(product % 24)));
            }
        }
    }

    private void seedOrders(LocalDate today, OffsetDateTime now) {
        String[] statuses = {"DRAFT", "ORDERED", "IN_TRANSIT", "DELAYED", "DELIVERED", "CANCELLED"};
        for (int i = 0; i < 60; i++) {
            int stage = i / 10;
            LocalDate orderDate = today.minusDays(25 + i % 10);
            LocalDate expected = stage == 3 ? today.minusDays(2 + i % 9)
                    : stage == 4 ? today.minusDays(3 + i % 5) : today.plusDays(2 + i % 12);
            BigDecimal total = BigDecimal.ZERO;
            for (int line = 0; line < 3; line++) {
                int product = (i * 3 + line) % 36;
                total = total.add(price(product).multiply(BigDecimal.valueOf(20 + (i + line) % 8 * 5)));
            }
            insert("purchase_orders", row("id", id("order", i), "order_code", code("PO", i), "supplier_id", id("supplier", i % 26),
                    "order_date", orderDate, "expected_delivery_date", expected, "status", statuses[stage], "total_amount", total,
                    "notes", "Đơn demo · " + statuses[stage], "created_at", now.minusDays(25 + i % 10), "updated_at", now.minusHours(i % 12)));
            for (int line = 0; line < 3; line++) {
                int product = (i * 3 + line) % 36;
                int quantity = 20 + (i + line) % 8 * 5;
                insert("purchase_order_items", row("id", id("order-item", i * 3 + line), "purchase_order_id", id("order", i),
                        "product_id", id("product", product), "quantity", quantity, "unit_price", price(product),
                        "line_total", price(product).multiply(BigDecimal.valueOf(quantity))));
            }
            if (stage == 0) continue;
            String shipmentStatus = new String[]{"", "PENDING", "SHIPPING", "DELAYED", "ARRIVED", "CANCELLED"}[stage];
            LocalDate departure = stage == 1 || stage == 5 ? null : orderDate.plusDays(2);
            insert("shipments", row("id", id("shipment", i), "shipment_code", code("SHIP", i), "purchase_order_id", id("order", i),
                    "carrier_name", new String[]{"Demo Express", "Demo Logistics", "Demo Transport"}[i % 3],
                    "departure_date", departure, "expected_arrival_date", expected,
                    "actual_arrival_date", stage == 4 ? expected.plusDays(1) : null,
                    "status", shipmentStatus, "current_location", stage == 4 ? "Đã đến kho" : "Trung tâm vận chuyển demo",
                    "notes", "Lịch sử vận chuyển mô phỏng; trạng thái và ngày tháng đã được kiểm tra"));
        }
    }

    private void seedManualRisks(OffsetDateTime now) {
        String[] impact = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};
        String[] statuses = {"OPEN", "INVESTIGATING", "RESOLVED", "CLOSED"};
        for (int i = 0; i < 18; i++) {
            insert("risk_events", row("id", id("risk", i), "title", "[DEMO] Kiểm tra chất lượng nguồn cung " + (i + 1),
                    "risk_type", "SUPPLIER", "impact_level", impact[i % 4], "description", "Tình huống mô phỏng để thử xử lý rủi ro thủ công",
                    "detected_at", now.minusHours(i * 3L), "status", statuses[(i / 4) % 4], "supplier_id", id("supplier", i), "source", "MANUAL"));
            String status = new String[]{"NEW", "READ", "RESOLVED"}[i % 3];
            insert("alerts", row("id", id("alert", i), "title", "[DEMO] Cảnh báo nguồn cung " + (i + 1),
                    "message", "Kiểm tra tình huống thử nghiệm và cập nhật tiến độ xử lý.", "alert_type", "SUPPLIER", "priority", impact[i % 4],
                    "status", status, "risk_event_id", id("risk", i), "resolved_at", status.equals("RESOLVED") ? now.minusHours(i) : null));
        }
    }

    private void seedDemand(LocalDate today) {
        for (int product = 0; product < 12; product++) {
            UUID mapping = id("mapping", product);
            insert("demand_series_mappings", row("id", mapping, "product_id", id("product", product), "source_dataset", "DEMO_SYNTHETIC",
                    "source_item_id", code("SP", product), "source_location_id", "DEMO_ALL_WAREHOUSES", "active", true));
            List<Object[]> rows = new ArrayList<>();
            for (int day = 0; day < 90; day++) {
                // Deterministic weekly/seasonal fixture variation; no learned metrics or fake M5 provenance.
                int quantity = Math.max(0, 9 + product * 2 + (day % 7 == 5 ? 8 : 0) + (day * 7 + product * 3) % 11 + day / 30);
                rows.add(new Object[]{id("demand-" + product, day), mapping, java.sql.Date.valueOf(today.minusDays(89 - day)), quantity,
                        "DEMO_" + day});
            }
            jdbc.batchUpdate("INSERT INTO demand_history (id, mapping_id, demand_date, quantity, source_day_key) VALUES (?, ?, ?, ?, ?)", rows);
        }
    }

    private void seedExternalEvents(LocalDate today, OffsetDateTime now) {
        String[] titles = {"Chậm thông quan tại cảng", "Tăng chi phí nguyên liệu", "Nguồn cung được khôi phục", "Mở tuyến vận tải mới"};
        for (int i = 0; i < 12; i++) {
            insert("external_events", row("id", id("external-event", i), "external_source", "DEMO_SYNTHETIC",
                    "external_event_id", code("EVENT", i), "event_date", today.minusDays(i % 3), "event_type", "DEMO_SCENARIO",
                    "event_code", "DEMO", "actor1_name", "[DEMO] " + titles[i % 4], "actor2_name", "Đối tác cung ứng mô phỏng",
                    "country", "Việt Nam", "country_code", "VN", "location", "Việt Nam",
                    "num_mentions", 0, "num_sources", 0, "num_articles", 0, "sentiment", "NOT_ANALYZED", "signal", "NEUTRAL"));
            for (int supplier = 0; supplier < 3; supplier++) {
                int score = new int[]{20, 45, 68, 86}[(i + supplier) % 4];
                String level = new String[]{"LOW", "MEDIUM", "HIGH", "CRITICAL"}[(i + supplier) % 4];
                insert("external_event_impacts", row("id", id("impact", i * 3 + supplier), "external_event_id", id("external-event", i),
                        "supplier_id", id("supplier", (i + supplier) % 26), "impact_score", score, "impact_level", level,
                        "impact_type", i % 4 < 2 ? "RISK" : "OPPORTUNITY_SIGNAL", "calculated_at", now.minusMinutes(i),
                        "explanation", "DEMO FIXTURE: điểm " + score + "/100 được đặt để thử giao diện, không phải kết quả scoring hay dự đoán AI. "
                                + "Sự kiện và đối tác đều là dữ liệu mô phỏng. Bấm tính lại để nhận kết quả từ quy tắc hiện tại."));
            }
        }
    }

    private void insert(String table, LinkedHashMap<String, Object> values) {
        String columns = String.join(", ", values.keySet());
        String placeholders = String.join(", ", Collections.nCopies(values.size(), "?"));
        Object[] parameters = values.values().stream().map(value -> value instanceof LocalDate date ? java.sql.Date.valueOf(date) : value).toArray();
        jdbc.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")", parameters);
    }

    private static LinkedHashMap<String, Object> row(Object... pairs) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }

    public static UUID id(String type, int index) {
        return UUID.nameUUIDFromBytes((VERSION + ":" + type + ":" + index).getBytes(StandardCharsets.UTF_8));
    }

    private static String code(String prefix, int index) { return "DEMO-" + prefix + "-" + String.format("%03d", index + 1); }
    private static BigDecimal price(int product) { return BigDecimal.valueOf(25000L + product * 12500L).setScale(2); }
}
