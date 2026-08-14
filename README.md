# Enterprise Risk Backend

Backend REST API cho **Hệ thống Giám sát, Phân tích và Dự báo Rủi ro Doanh nghiệp & Chuỗi cung ứng**.

## Giai đoạn 2

Branch hoàn thiện: `phase2/complete-backend`.

Tech stack giữ nguyên: Java 21, Spring Boot 3.5.x, Maven 3.9.x, PostgreSQL 17, Spring Data JPA, Jakarta Validation, Flyway, Lombok và Docker Compose v2.

### Cấu hình chuẩn

- Backend: `http://localhost:8080`
- PostgreSQL: `localhost:5432`
- Database: `enterprise_risk_db`
- Username/password dev: `postgres/postgres`

Máy Windows đang có PostgreSQL local chiếm 5432 có thể đặt `DB_PORT=5433` trong `.env`; `scripts/run-dev.ps1` cũng hỗ trợ cấu hình này và tự đồng bộ mật khẩu volume cũ.

## Chạy trên Windows

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\run-dev.ps1
```

Reset database dev (xóa volume và dữ liệu dev):

```powershell
.\scripts\run-dev.ps1 -ResetDatabase
```

## Migration

- `V1__init_schema.sql`: 17 bảng nghiệp vụ nền tảng.
- `V2__seed_roles.sql`: seed `ADMIN`, `MANAGER`, `STAFF`, `ANALYST` bằng `ON CONFLICT DO NOTHING`.
- Hibernate dùng `ddl-auto: validate`, Flyway không bị disable.

## API Giai đoạn 2

- Suppliers: `GET/POST /api/suppliers`, `GET/PUT/DELETE /api/suppliers/{id}` (DELETE là soft delete).
- Product categories: `GET/POST /api/product-categories`, `PUT /api/product-categories/{id}`.
- Products: `GET/POST /api/products`, `GET/PUT/DELETE /api/products/{id}`.
- Warehouses: `GET/POST /api/warehouses`, `GET/PUT /api/warehouses/{id}`.
- Inventory: `GET/POST /api/inventory`, `GET/PUT /api/inventory/{id}`, `GET /api/inventory/low-stock`.
- Purchase orders: `GET/POST /api/purchase-orders`, `GET/PUT /api/purchase-orders/{id}`, `PATCH /api/purchase-orders/{id}/status`.
- Shipments: `GET/POST /api/shipments`, `GET/PUT /api/shipments/{id}`, `PATCH /api/shipments/{id}/status`.
- Risk events: `GET/POST /api/risk-events`, `GET/PUT /api/risk-events/{id}`, `PATCH /api/risk-events/{id}/status`.
- Alerts: `GET/POST /api/alerts`, `GET /api/alerts/{id}`, `PATCH /api/alerts/{id}/status`.
- Companies: `GET/POST /api/companies`, `PUT /api/companies/{id}`.
- Departments: `GET/POST /api/departments`, `PUT /api/departments/{id}`.
- Employees: `GET/POST /api/employees`, `PUT /api/employees/{id}`.
- Dashboard: `GET /api/dashboard/summary`.

API dùng request/response DTO thay vì expose Entity cho các module nghiệp vụ. Validation, 404, 400, 409 và 500 được chuẩn hóa qua global exception handler.

## Build và test

```powershell
.\mvnw.cmd -f .\backend\pom.xml clean test
.\mvnw.cmd -f .\backend\pom.xml clean package
```

GitHub Actions chạy hai lệnh trên với PostgreSQL 17 thật. Integration test kiểm tra CRUD Supplier, validation, duplicate, UUID không tồn tại, reference không tồn tại và luồng end-to-end Supplier → Product → Warehouse → Inventory → Purchase Order → Shipment → Risk Event → Alert → Dashboard.

## Chưa thuộc Giai đoạn 2

Không triển khai React, JWT/Spring Security, OAuth2, FastAPI/AI, Neo4j, WebSocket, Kafka, Redis, Kubernetes, Elasticsearch hoặc cloud deployment.
