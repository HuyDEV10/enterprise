# Enterprise Risk Backend

Backend cho hệ thống giám sát, phân tích và dự báo rủi ro doanh nghiệp & chuỗi cung ứng.

## Yêu cầu

- Java 21
- Docker Desktop + Docker Compose v2
- Windows PowerShell (khuyến nghị cho script chạy nhanh)

## Cấu hình mặc định

- Spring Boot: `http://localhost:8080`
- PostgreSQL Docker host port: `5433`
- PostgreSQL container port: `5432`
- Database: `enterprise_risk_db`
- Username: `postgres`
- Password dev mặc định: `postgres`

PostgreSQL Docker dùng host port `5433` để tránh xung đột với PostgreSQL cài trực tiếp trên Windows thường dùng `5432`.

## Chạy nhanh trên Windows

Từ thư mục gốc project:

```powershell
.\scripts\run-dev.ps1
```

Nếu trước đây đã tạo database Docker với cấu hình/password khác và muốn khởi tạo sạch:

```powershell
.\scripts\run-dev.ps1 -ResetDatabase
```

> `-ResetDatabase` xóa volume PostgreSQL của project và toàn bộ dữ liệu dev trong volume đó.

## Chạy thủ công

```powershell
docker compose up -d postgres
docker compose ps
.\mvnw.cmd -f .\backend\pom.xml spring-boot:run
```

Không chạy `spring-boot:run` trực tiếp ở root mà không truyền `-f`, vì Maven project của backend nằm trong `backend/pom.xml`.

## Cấu hình môi trường

Copy `.env.example` thành `.env` nếu muốn đổi cấu hình Docker:

```powershell
Copy-Item .env.example .env
```

Spring Boot hỗ trợ các biến:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `SERVER_PORT`

## Database migration

Flyway migration nằm tại:

`backend/src/main/resources/db/migration/V1__init_schema.sql`

Hibernate chạy với `ddl-auto: validate`, vì vậy schema luôn được kiểm tra với Entity khi application khởi động.

## CI

GitHub Actions chạy Java 21 + PostgreSQL 17 thật và thực hiện:

```bash
./mvnw -f backend/pom.xml clean verify
```

CI sẽ phát hiện lỗi compile, lỗi Spring context, Flyway migration và Hibernate schema validation.
