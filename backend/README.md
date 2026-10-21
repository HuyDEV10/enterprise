# Backend development and hardening

Java 21, Maven Wrapper 3.9.16, Spring Boot 3.5, PostgreSQL 17 and Flyway. Run commands from the repository root. Frontend and FastAPI source are unchanged by this backend update.

## Configuration and startup

Direct startup requires `JWT_SECRET`: a unique Base64-encoded key containing at least 32 cryptographically random bytes. There is no application fallback. Generate a local key without printing it:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export DB_PORT=5433
docker compose up -d postgres
sh ./mvnw -f backend/pom.xml spring-boot:run
```

Match DB_PORT, DB_NAME, DB_USER and DB_PASSWORD to your database. Use environment settings or an ignored local `.env` for credentials. Spring Boot does not automatically load the root `.env`; the Windows development launcher reads supported entries from it.

On Windows, `scripts/run-dev.ps1` creates a process-local random JWT key when no key is configured. Existing JWTs then become invalid after restart. Set a persistent unique key for a stable development session; a production profile always requires an explicit key. The launcher does not print or persist generated keys.

Bootstrap admin is **disabled by default**. To create the first administrator, explicitly set BOOTSTRAP_ADMIN_ENABLED=true and supply BOOTSTRAP_ADMIN_PASSWORD, optionally BOOTSTRAP_ADMIN_USERNAME/EMAIL/FULL_NAME. The password must have at least 12 characters and at most 72 UTF-8 bytes. Disable bootstrap after provisioning. Existing accounts are never silently overwritten. The frontend's old demo credentials are no longer backend defaults.

For a database that already contains a demo administrator, create a new ADMIN with a private password through `/api/users`, verify that account, then disable the old demo account. Removing configuration defaults does not rotate existing database passwords.

Login failures are limited by normalized account and remote address: default 5 failures in 60 seconds, configurable with `app.auth.login.max-failures` and `app.auth.login.window-ms`. A blocked request returns 429, AUTH_RATE_LIMITED and Retry-After. Counters are bounded and local to an application instance; use shared enforcement at a trusted gateway when running multiple instances. Configure trusted proxy address handling when deploying behind a reverse proxy.

## Migrations

Flyway remains enabled and Hibernate validates the schema. V1–V5 are unchanged.

- V6 preserves historical demand mappings and keeps the most recently updated active mapping per product, with created_at/id as deterministic tie-breakers. It adds a partial unique index enforcing one active mapping per product.
- V7 adds optimistic-lock versions to entities derived from BaseEntity. A conflicting concurrent update returns 409/CONCURRENT_UPDATE.

Review the V6 choice against existing duplicate mappings before deploying. Existing tables and demand history are retained. Optimistic locking detects overlapping write transactions; a stale form submitted after another transaction has already committed still needs a client-provided version/ETag contract for full stale-form protection.

## Purchase order and shipment rules

New purchase orders start in DRAFT. Supplier, items and amounts can only be edited while DRAFT. Allowed transitions:

| Current | Next |
| --- | --- |
| DRAFT | ORDERED, CANCELLED |
| ORDERED | IN_TRANSIT, DELAYED, CANCELLED |
| IN_TRANSIT | DELIVERED, DELAYED, CANCELLED |
| DELAYED | ORDERED, IN_TRANSIT, DELIVERED, CANCELLED |
| DELIVERED / CANCELLED | No reopening |

Repeating the current status is idempotent. Closing or cancelling an order requires completing/cancelling its active shipments. New shipments start in PENDING and require an ORDERED/IN_TRANSIT/DELAYED purchase order. PENDING can move to SHIPPING/DELAYED/CANCELLED; SHIPPING or DELAYED can move to ARRIVED/CANCELLED or the appropriate active state. ARRIVED and CANCELLED are terminal.

Starting a shipment advances an ORDERED parent to IN_TRANSIT. ARRIVED records the actual arrival date. Shipment creation/update cannot supply a premature actual arrival; dates must be coherent. Terminal shipments cannot be edited, and an in-progress shipment cannot be reassigned. Shipment arrival does not automatically book stock receipts or finish a multi-shipment purchase order; receiving/stock movements remain a separate workflow.

User creation, profile/status/role changes, purchase order and shipment changes, inventory changes and demand imports write audit records in their transaction. Passwords and JWTs are excluded. A shared database lock protects the last active ADMIN against concurrent role/status changes.

## AI and demand import

The injected Spring Boot RestClient.Builder sends LocalDate values as ISO strings. The client validates the 28 forecast dates, finite/nonnegative quantities and consistent aggregate totals before risk calculations.

AI input errors return 422 with a stable code; missing resources return 404; invalid upstream responses return 502; unavailable services return 503; timeouts return 504. Response bodies from upstream are not copied into error messages. Existing M5 import validates identity and contiguous, nonnegative history before opening the write transaction. PostgreSQL upserts preserve row identity across repeated imports. Replacing the source changes the active mapping atomically while retaining old history.

503 error codes distinguish MODEL_UNAVAILABLE, M5_DATASET_UNAVAILABLE, AI_UNREACHABLE, AI_DISABLED and AI_RATE_LIMITED from a generic AI_UNAVAILABLE response.

Forecast reads at most the latest 365 history rows. M5 remains research/demo data and requires the real CSV dataset in FastAPI for import. This change does not retrain models, change FastAPI's calendar-rebasing behavior, or redesign entity-level sentiment persistence.

## Read APIs and errors

Existing list endpoints keep their array response for frontend compatibility. New bounded endpoints:

```text
GET /api/users/page?page=0&size=20&sort=username,asc
GET /api/suppliers/page?page=0&size=20&riskLevel=HIGH
GET /api/purchase-orders/page?page=0&size=20&sort=createdAt,desc
```

They return content, page, size, totalElements and totalPages. Page size is capped at 100, supported sort fields are whitelisted and id is used as a stable tie-breaker. Users remain ADMIN-only. User list roles and page collections use fetch graphs/batching where applicable. Other legacy lists remain available for gradual frontend migration.

JSON/enum/parameter validation returns 400. Error responses retain timestamp/status/error/message/path and add code/requestId. X-Request-ID is returned in response headers and exposed through CORS. Unexpected errors are logged with that ID. Authentication-store outages return 503 rather than appearing as invalid tokens.

## Verification

With PostgreSQL available, run against a dedicated test database:

```bash
DB_PORT=5432 DB_NAME=enterprise_backend_tests sh ./mvnw -f backend/pom.xml clean verify
```

The database must exist. Test resources supply an explicitly test-only JWT key, disable bootstrap and background schedulers, and are not packaged into the application. Tests cover JWT/bootstrap validation, role protection/audit, request error status, login throttling, order/shipment transitions, actual AI HTTP serialization/error handling, repeatable demand imports, mapping replacement and stock writers using independent database transactions.

The Windows launcher requires PowerShell/Windows for runtime validation; Linux backend tests do not execute it. Real AI inference can be checked by starting FastAPI with the repository's trained artifacts and calling the forecast API through Spring Boot with an active mapping and contiguous history. API fixtures in tests are never used for model training or quality metrics.
