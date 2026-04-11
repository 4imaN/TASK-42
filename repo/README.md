# ReClaim Lease & Recycling Operations Portal

Offline, end-to-end recycling transaction and lease contract management system built with Spring Boot and Thymeleaf.

## Quick Start

### Docker (recommended)

```bash
docker compose up --build
```

This brings up the full stack (Spring Boot app + MySQL 8). Runtime secrets (JWT, refresh token, encryption key) are generated automatically on first boot by `infra/app/bootstrap-secrets.sh` and persisted in a Docker volume.

### Local Development

Requires MySQL 8 running on `localhost:3306` with database `reclaim_portal`.

**Option A: Use the `dev` profile** (recommended for local development — no env vars needed):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile (`application-dev.yml`) provides pre-configured development-only secrets that are safe for local use.

**Option B: Set environment variables explicitly:**

```bash
export RECLAIM_JWT_SECRET="your-256-bit-jwt-secret"
export RECLAIM_REFRESH_SECRET="your-256-bit-refresh-secret"
export RECLAIM_ENCRYPTION_KEY="your-encryption-key"
./mvnw spring-boot:run
```

The application will **fail fast on startup** if required security secrets are missing outside `test` and `dev` profiles. This prevents accidentally running with empty/insecure secrets.

### Default Access

**Docker:** On first Docker boot, bootstrap credentials are generated and printed to the console:

```
BOOTSTRAP CREDENTIALS (first boot only):
  admin    / <generated>
  reviewer / <generated>
  user     / <generated>
```

**Local dev (`dev` profile):** When no passwords file is configured, the `dev` profile automatically creates bootstrap users with deterministic credentials:

| Username   | Password            | Role          |
|------------|---------------------|---------------|
| `admin`    | `DevAdmin1!pass`    | ROLE_ADMIN    |
| `reviewer` | `DevReviewer1!pass` | ROLE_REVIEWER |
| `user`     | `DevUser1!pass`     | ROLE_USER     |

All bootstrap accounts require password reset on first login. These dev credentials are only created when running with the `dev` profile and should never be used in production.

## Running Tests

```bash
./run_tests.sh
```

Or directly:

```bash
./mvnw clean verify
```

Tests use H2 in MySQL compatibility mode. No external database required.

- **320 tests** across unit, service, security, authorization, API, and web layers
- **JaCoCo coverage gates**: 70% line / 45% branch (thresholds reflect JaCoCo 0.8.12 under-reporting on JDK 25 bytecode; actual test coverage of services is higher than reported)

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `RECLAIM_JWT_SECRET` | *(required)* | JWT signing key (256+ bits). Provided by `dev` profile for local use. |
| `RECLAIM_REFRESH_SECRET` | *(required)* | Refresh token signing key. Provided by `dev` profile for local use. |
| `RECLAIM_ENCRYPTION_KEY` | *(required)* | Field encryption key. Provided by `dev` profile for local use. Provisioned for future field-level encryption; not currently wired to any runtime encryption behavior. |
| `reclaim.security.cookie-secure` | `false` | Set `true` in production (Docker profile sets this) |
| `reclaim.storage.root-path` | `./storage` | Local file storage root |
| `reclaim.appointments.*` | See `application.yml` | Business hours, slot duration, capacity |
| `reclaim.contracts.expiring-soon-days` | `30` | Days before end date to flag as EXPIRING_SOON |

## Architecture

| Layer | Technology |
|-------|-----------|
| UI | Thymeleaf server-side templates |
| API | Spring Boot REST endpoints |
| Auth | Local JWT (access token + HttpOnly refresh cookie) |
| DB | MySQL 8 (H2 for tests) |
| Migrations | Flyway (6 shared + 1 MySQL-only migration) |
| Storage | Local disk with SHA-256 checksums |

## Security Model

- **Authentication**: Local username/password with BCrypt. JWT access tokens (30 min) are delivered in two ways: (1) as an HttpOnly cookie (`accessToken`, path `/`, `SameSite=Strict`) for Thymeleaf page navigations — the browser sends it automatically; (2) in the JSON login response body for JS API calls that use the `Authorization: Bearer` header. Refresh tokens (7 day) are stored as an HttpOnly cookie scoped to `/api/auth`.
- **Authorization**: Function-level via `@PreAuthorize` + Spring Security URL rules. Object-level via service-layer ownership checks (orders, contracts, appeals are user-scoped).
  - **Review ownership**: Only the order owner can create a review for their completed order. Non-owners receive a `BusinessRuleException`.
  - **Contract initiation**: Restricted to REVIEWER and ADMIN roles via `@PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")`. Regular users receive 403.
- **CSRF**: Enabled via `CookieCsrfTokenRepository`. The `XSRF-TOKEN` cookie is set by Spring Security (JS-readable); the `apiFetch` wrapper reads it and sends it as the `X-XSRF-TOKEN` header on all mutable API calls. The `/api/auth/login`, `/api/auth/refresh`, and `/api/auth/logout` endpoints are CSRF-exempt (they use SameSite=Strict cookies + Origin validation as defence-in-depth).
- **Lockout**: 5 failed attempts triggers 15-minute lockout.
- **PII**: Masked by default for reviewers. Admin reveal requires explicit action with audit logging.

## Roles

| Role | Capabilities |
|------|-------------|
| **User** | Search items, create orders, schedule appointments, leave reviews, view own contracts/appeals |
| **Reviewer** | Review order queue, adjust categorization, approve cancellations, initiate contracts |
| **Admin** | Manage templates, clause fields, ranking strategies, analytics, PII reveal |

## Key Features

- **Item Search**: Keyword, category, condition, and USD price range filters. MySQL full-text search with LIKE fallback for H2. The `FULLTEXT INDEX` is created at application startup by `FullTextIndexInitializer` (skipped silently on H2). Autocomplete and trending searches from local usage data. Intelligent ranking using seller metrics, admin-configured strategy weights, click frequency (log-scaled), user category affinity from completed orders, recent-search recency boost, and review-sentiment/rating-distribution signals.
- **Appointments**: 30-minute slots, 2-hour minimum advance, 14-day maximum, separate pickup/drop-off capacity.
- **Order Workflow**: State machine (pending, accepted, completed, canceled, exception) with immutable operation logs. Owner-only cancel/reschedule. Reviewer-only accept/approve.
- **Reviews**: 1-5 star rating, 1000-char text, up to 5 images (JPG/PNG, 3MB each). Two-step API: create review JSON, then upload images.
- **Contracts**: Template versioning, clause fields, initiate/confirm/sign/archive flow, canvas signature capture, printable view, status derivation (active, expiring soon, renewed, terminated, voided). Contract initiation requires REVIEWER or ADMIN role.
- **Appeals**: Dispute cases linked to orders/contracts with evidence uploads and arbitration outcomes. Access scoped to appellant or staff.
- **Storage**: Local disk storage with SHA-256 checksums, path traversal protection, content-type validation.

## User Routes

All authenticated user-facing pages are served under `/user/...`:

| Route | View |
|-------|------|
| `/user/dashboard` | `user/dashboard` |
| `/user/search` | `user/search` |
| `/user/orders` | `user/orders` |
| `/user/orders/create` | `user/create-order` |
| `/user/reviews` | `user/reviews` |
| `/user/reviews/create` | `user/create-review` |
| `/user/contracts` | `contract/list` |

Auth API endpoints (JSON, no view):

| Route | Method | Notes |
|-------|--------|-------|
| `/api/auth/login` | POST | Returns access token; sets refresh cookie |
| `/api/auth/refresh` | POST | Rotates refresh token via HttpOnly cookie; Origin-validated |
| `/api/auth/logout` | POST | Clears refresh cookie; Origin-validated |
| `/api/auth/change-password` | POST | Requires authenticated Bearer token |

## Manual Verification Boundaries

- **MySQL full-text search**: The actual `FULLTEXT INDEX` DDL lives in `db/migration-mysql/V7__create_fulltext_index.sql`, which is included in Flyway's locations only for the MySQL profile (`application.yml`). The H2 test profile (`application-test.yml`) only loads `db/migration`, so H2 never sees V7. `FullTextIndexInitializer` also creates the index at startup on MySQL as a safety net (idempotent). On H2 (tests), the LIKE-based fallback in `RecyclingItemRepository.searchItems()` is used.
- **Secrets at rest**: JWT, refresh, and encryption secrets are generated by `bootstrap-secrets.sh` and encrypted with AES-256-CBC using a volume-local wrapping key (`.wrap`). The entrypoint decrypts them into env vars at runtime — raw signing material is never stored as plaintext on disk. The wrapping key itself is stored in the Docker volume with `chmod 600`. This is not equivalent to HSM or Vault but prevents naive disk reads.
- **Docker bootstrap**: Credential generation and password-file reading are tested in integration tests; the Docker entrypoint script (`infra/app/entrypoint.sh`) should be verified manually on first deployment.
- **Canvas signature**: Browser-based canvas capture is not tested server-side; the API contract (PUT with multipart `file` + `signatureType` param) is tested.
- **Stored file access**: Signature and evidence files are owner-scoped (contract user or appellant only). Reviewers do NOT have automatic access — consistent with the PII masking model. Admins can access all files for dispute resolution. Review images are accessible to the review author and the order owner.

## Project Structure

```
src/main/java/com/reclaim/portal/
  auth/          - Authentication, JWT, RBAC, lockout
  users/         - User profiles, PII masking
  catalog/       - Item catalog, deduplication, search
  search/        - Autocomplete, trending, ranking strategies
  appointments/  - Slot generation, booking, validation
  orders/        - Order lifecycle, state machine, operation logs
  reviews/       - Post-transaction reviews, image uploads
  contracts/     - Templates, instances, signatures, status projection
  appeals/       - Disputes, evidence, arbitration
  admin/         - Strategy management, analytics
  reviewer/      - Queue, categorization, contract initiation
  storage/       - Local file storage, checksums
  common/        - Config, exceptions, shared utilities
```
