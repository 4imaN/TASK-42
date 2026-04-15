# ReClaim Lease & Recycling Operations Portal

**Project type: fullstack** — Spring Boot REST API + Thymeleaf server-rendered UI.

## Start

```bash
docker-compose up
```

(Also works with the newer `docker compose up` syntax.)

## Access

Open **http://localhost:8080** — the login page is served at `/login`.

## Demo credentials

Deterministic credentials are provisioned on first Docker boot (encrypted at rest, decrypted by the entrypoint):

| Role     | Username   | Password              |
|----------|------------|-----------------------|
| Admin    | `admin`    | `AdminDemo1!pass`     |
| Reviewer | `reviewer` | `ReviewerDemo1!pass`  |
| User     | `user`     | `UserDemo1!pass`      |

All three accounts force a password reset on first login. Override the defaults by setting `RECLAIM_ADMIN_PASS`, `RECLAIM_REVIEWER_PASS`, `RECLAIM_USER_PASS` in the environment before the first `docker-compose up`.

## Verification

1. Run `docker-compose up` and wait until the logs print `Started ReclaimPortalApplication`.
2. Open **http://localhost:8080** — you should be redirected to `/login`.
3. Log in as `user` / `UserDemo1!pass` → change the password when prompted → you land on `/user/dashboard` showing your order counts.
4. Navigate to **Search**, enter a keyword, verify the catalog renders; click **+ Add** on an item → the selection count badge increments.
5. Go to **Orders → New Order**, pick a date, select a 30-minute slot, submit → you land on the order detail page with status **PENDING_CONFIRMATION**.
6. Log out, log in as `reviewer` / `ReviewerDemo1!pass`, change the password → open **Reviewer → Queue**, click the user's order → **Accept Order** → status flips to **ACCEPTED**.
7. Log in as `admin` / `AdminDemo1!pass` → **Admin → Strategies** shows the current ranking strategy; **Admin → Analytics** shows the search analytics populated from step 4.

Each step above is covered by automated tests (`UserJourneyE2ETest`, `BrowserLoginJourneyTest`, `BrowserMultiPageNavigationTest`), so a green test run is a strong proxy for successful manual verification.

## Stopping

```bash
docker-compose down
```

Add `-v` to also drop the persisted MySQL + secrets volumes (forces fresh secret generation on next start).

## Running Tests (Docker)

```bash
./run_tests.sh
```

The script builds a test image (`Dockerfile.test`) and runs the full suite inside a container — no local JDK or Maven needed. Tests use H2 in MySQL compatibility mode.

- **576 tests** across unit, service, security, authorization, API, web, browser (HtmlUnit), and real-HTTP layers
- **JaCoCo coverage gates**: 70% line / 45% branch (thresholds reflect JaCoCo 0.8.12 under-reporting on JDK 25 bytecode; actual runtime coverage is higher)

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

## Test Coverage Overview

| Layer | Test pattern | Count |
|-------|--------------|-------|
| Unit (pure logic) | `src/test/java/**/unit/*Test.java` | ~50 |
| Service integration (Spring + H2) | `src/test/java/**/service/*IntegrationTest.java` | ~130 |
| API contract (MockMvc + real JWT) | `src/test/java/**/api/*Test.java` | ~100 |
| Security / authorization | `src/test/java/**/security/*Test.java` | ~30 |
| Web / page controller | `src/test/java/**/web/*Test.java` | ~30 |
| End-to-end MockMvc journeys | `src/test/java/**/e2e/UserJourneyE2ETest.java` | 2 multi-step flows |
| Browser (HtmlUnit, real HTTP) | `src/test/java/**/e2e/BrowserLoginFlowTest.java`, `BrowserLoginJourneyTest.java`, `BrowserMultiPageNavigationTest.java` | 14 tests — static assets, programmatic login, multi-page nav |
| MySQL native full-text (Testcontainers) | `src/test/java/**/catalog/MySqlFullTextSearchIT.java` | 4 tests, gated on `RUN_MYSQL_IT=true` |

### Running the MySQL integration tests

The MySQL `MATCH/AGAINST` path is exercised by `MySqlFullTextSearchIT` via Testcontainers. It is disabled by default and runs only when `RUN_MYSQL_IT=true` is set:

```bash
# Runs inside the Docker-built test image with Testcontainers-in-Docker enabled.
docker build -f Dockerfile.test -t reclaim-tests . && \
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
    -e RUN_MYSQL_IT=true reclaim-tests
```

## Manual Verification Boundaries

- **MySQL full-text search**: `FullTextSearchFallbackTest` validates the H2 fallback path; `MySqlFullTextSearchIT` exercises the real `MATCH/AGAINST` query against a Testcontainers MySQL 8 instance (gated on `RUN_MYSQL_IT=true`). No remaining automated gap — production behavior is covered when Docker is available.
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
