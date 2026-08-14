# Enterprise Risk Backend

Backend cho hệ thống giám sát, phân tích và dự báo rủi ro doanh nghiệp & chuỗi cung ứng.

## Yêu cầu

- Java 21
- Docker Desktop + Docker Compose v2
- Windows PowerShell

## Cấu hình mặc định

- Spring Boot: `http://localhost:8080`
- PostgreSQL Docker host port: `5433`
- PostgreSQL container port: `5432`
- Database: `enterprise_risk_db`
- Username: `postgres`
- Password dev mặc định: `postgres`

PostgreSQL Docker dùng host port `5433` để tránh xung đột với PostgreSQL cài trực tiếp trên Windows thường dùng `5432`.

## Cách chạy khuyến nghị trên Windows

Từ thư mục gốc project:

```powershell
.\scripts\run-dev.ps1
```

Script sẽ tự động:

1. Đọc cấu hình `DB_*` từ biến môi trường hoặc `.env`.
2. Khởi động PostgreSQL Docker.
3. Chờ container healthy.
4. Đồng bộ password của role PostgreSQL với `DB_PASSWORD` ngay cả khi volume được tạo từ cấu hình cũ.
5. Kiểm tra lại TCP authentication bằng đúng user/password mà Spring Boot sẽ sử dụng.
6. Chạy backend bằng đúng `backend/pom.xml`.

Vì vậy lỗi `FATAL: password authentication failed for user "postgres"` do volume cũ sẽ được tự xử lý mà không cần xóa dữ liệu.

Nếu volume phát triển không cần giữ và muốn khởi tạo hoàn toàn sạch:

```powershell
.\scripts\run-dev.ps1 -ResetDatabase
```

> `-ResetDatabase` xóa volume PostgreSQL của project và toàn bộ dữ liệu dev trong volume đó.

## Cấu hình môi trường

Copy `.env.example` thành `.env` nếu muốn đổi cấu hình:

```powershell
Copy-Item .env.example .env
```

Các biến được hỗ trợ:

- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `SERVER_PORT`

Backend chỉ dùng bộ `DB_*` này cho cấu hình database local, tránh tình trạng các biến `SPRING_DATASOURCE_*` cũ trên Windows ghi đè nhầm cấu hình.

## Chạy Maven thủ công

Nếu database đã được chuẩn bị đúng, có thể chạy:

```powershell
.\mvnw.cmd -f .\backend\pom.xml spring-boot:run
```

Không chạy `spring-boot:run` trực tiếp ở root mà không truyền `-f`, vì Maven project nằm trong `backend/pom.xml`.

## Database migration

Flyway migration nằm tại:

`backend/src/main/resources/db/migration/V1__init_schema.sql`

Hibernate chạy với `ddl-auto: validate`, vì vậy schema luôn được kiểm tra với Entity khi application khởi động.

## CI

GitHub Actions dùng Java 21 + PostgreSQL 17 thật và kiểm tra:

- cú pháp `scripts/run-dev.ps1`;
- Maven compile/test;
- Spring context;
- Flyway migration;
- Hibernate schema validation.

Lệnh Maven trong CI:

```bash
./mvnw -f backend/pom.xml clean verify
```
