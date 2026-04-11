# ReClaim Portal - System Design Document

## 1. Overview

ReClaim is an offline, end-to-end recycling transaction and lease contract management portal built with Spring Boot 3.2 and Thymeleaf. It manages the full lifecycle of recycling item transactions: catalog search, order placement, appointment scheduling, reviewer processing, contract generation/signing, reviews, and appeals.

## 2. Architecture

### 2.1 Technology Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.2.5, Java 17 |
| Templating | Thymeleaf 3.1 with Layout Dialect |
| Database | MySQL 8.0 (production), H2 in MySQL mode (tests) |
| Migrations | Flyway 9.22 |
| Authentication | JWT (HMAC-SHA512) via jjwt 0.12 |
| Password Hashing | BCrypt |
| Build | Maven, JaCoCo coverage |
| Containerization | Docker Compose (app + MySQL) |

### 2.2 High-Level Architecture

```
Browser
  |
  |-- GET pages --> Thymeleaf SSR (cookie-based JWT auth)
  |-- fetch() ----> REST API (Bearer token or cookie JWT auth)
  |
  v
+----------------------------------------------------------+
|  Spring Security Filter Chain                            |
|  CsrfFilter -> CsrfCookieForceFilter -> JwtAuthFilter   |
+----------------------------------------------------------+
  |
  v
+----------------------------------------------------------+
|  Controllers (API + Page)                                |
|  @PreAuthorize role checks                               |
+----------------------------------------------------------+
  |
  v
+----------------------------------------------------------+
|  Services (business logic, ownership checks)             |
+----------------------------------------------------------+
  |
  v
+----------------------------------------------------------+
|  Spring Data JPA Repositories                            |
+----------------------------------------------------------+
  |
  v
+----------------------------------------------------------+
|  MySQL 8.0 / H2 (Flyway-managed schema)                 |
+----------------------------------------------------------+
```

### 2.3 Package Structure

```
com.reclaim.portal
  admin/          - Admin dashboard, ranking strategies, analytics
  appeals/        - Dispute resolution and arbitration
  appointments/   - Appointment slot management
  auth/           - Authentication, JWT, user management
  catalog/        - Recycling item catalog, search, deduplication
  common/         - Security config, exception handling, properties
  contracts/      - Contract templates, lifecycle, signatures
  orders/         - Order state machine, operation logs
  reviewer/       - Reviewer workflows
  reviews/        - User reviews and images
  search/         - Full-text search, ranking, analytics
  storage/        - File storage with checksums
  users/          - User-facing page controllers
```

## 3. Security Model

### 3.1 Authentication

- **JWT tokens**: Access tokens (30 min, HMAC-SHA512) and refresh tokens (7 days)
- **Dual delivery**: Access token returned in JSON response AND set as HttpOnly cookie (for SSR page navigation)
- **Refresh flow**: Refresh token in HttpOnly cookie scoped to `/api/auth`, SameSite=Strict
- **Stateless sessions**: `SessionCreationPolicy.STATELESS` - no server-side session state
- **Account lockout**: 5 failed attempts triggers 15-minute lock. Disabled and locked accounts are rejected at login, token refresh, and JWT filter levels
- **Bootstrap accounts**: Created on first boot with `forcePasswordReset=true`
- **Password policy**: Minimum 12 characters, requires uppercase, lowercase, digit, and special character

### 3.2 Authorization (RBAC)

| Role | Capabilities |
|------|-------------|
| ROLE_USER | Search catalog, create orders, write reviews, sign contracts, file appeals |
| ROLE_REVIEWER | Accept/complete orders, initiate contracts, recategorize items, resolve appeals |
| ROLE_ADMIN | Manage ranking strategies, view analytics, reveal PII, manage templates, all reviewer capabilities |

Authorization is enforced at two levels:
1. **URL-level**: `SecurityConfig` rules and `@PreAuthorize` annotations
2. **Object-level**: Service methods verify ownership (e.g., `requireOrderOwnerOrStaff`)

### 3.3 CSRF Protection

- `CookieCsrfTokenRepository` with JS-readable `XSRF-TOKEN` cookie
- Plain `CsrfTokenRequestAttributeHandler` (not XOR) so raw cookie value matches header
- `CsrfCookieForceFilter` eagerly loads token on every response
- Auth endpoints (`/api/auth/*`) exempt (use SameSite=Strict + Origin validation instead)
- `apiFetch()` in `app.js` reads cookie and includes `X-XSRF-TOKEN` header on all requests

### 3.4 File Storage Security

- UUID-based filenames prevent enumeration
- SHA-256 checksums stored and verified on retrieval (evidence, signatures, review images)
- Magic byte validation prevents content-type spoofing (PNG: `89504E47`, JPEG: `FFD8FF`)
- Path traversal protection in StorageService
- Ownership-based access control per subdirectory:
  - `signatures/` - contract owner only (PII protection)
  - `evidence/` - uploader or contract/appeal owner
  - `reviews/` - review author, order owner, or assigned reviewer
- Admin has unrestricted file access (audit/dispute resolution)

## 4. Data Model

### 4.1 Entity Relationship Diagram

```
users ──< user_roles >── roles
  |
  +──< orders ──< order_items ──> recycling_items
  |      |                              |
  |      +──< order_operation_logs      +──> seller_metrics
  |      |
  |      +──< contract_instances ──> contract_template_versions ──> contract_templates
  |      |         |                        |
  |      |         +──< signature_artifacts +──< contract_clause_fields
  |      |         +──< evidence_files
  |      |
  |      +──< reviews ──< review_images
  |      |
  |      +──< appeals ──< arbitration_outcomes
  |                +──< evidence_files
  |
  +──< search_logs ──< search_click_logs
  +──< login_attempts
  +──< refresh_tokens
  +──< admin_access_logs
  +──< ranking_strategy_versions

recycling_items ──< item_fingerprints
search_trends (standalone)
appointments (standalone, referenced by orders)
```

### 4.2 Key Tables

| Table | Records | Purpose |
|-------|---------|---------|
| `users` | Core identity | Username, BCrypt hash, enabled/locked state, seller credit score |
| `roles` | 3 seeded | ROLE_USER, ROLE_REVIEWER, ROLE_ADMIN |
| `recycling_items` | 20 seeded | Catalog with title, category, condition, price, seller_id |
| `seller_metrics` | Per seller | Credit score, positive rate, review quality for ranking |
| `orders` | Per transaction | Status machine with optimistic locking (`@Version`) |
| `order_operation_logs` | Append-only | Immutable audit trail (JPA callbacks + MySQL triggers) |
| `contract_instances` | Per contract | Lifecycle with optimistic locking, rendered content |
| `ranking_strategy_versions` | Admin-managed | Configurable weights for search ranking algorithm |
| `appointments` | Auto-generated | 30-min slots with PICKUP/DROPOFF capacity tracking |

### 4.3 Immutability Enforcement

`order_operation_logs` is append-only:
- **Application layer**: `@PreUpdate`/`@PreRemove` JPA callbacks throw `UnsupportedOperationException`
- **Repository layer**: Interface extends marker `Repository` (not `JpaRepository`), exposing only `save()` and read methods
- **Database layer**: MySQL `BEFORE UPDATE`/`BEFORE DELETE` triggers (`SIGNAL SQLSTATE '45000'`)

## 5. Order State Machine

```
                    +-----------+
                    |  PENDING  |
                    |CONFIRMATION|
                    +-----+-----+
                          |
              +-----------+-----------+
              |                       |
        [accept]                [cancel by owner]
              |                       |
              v                       v
        +-----------+          +-----------+
        |  ACCEPTED |          |  CANCELED |  (if > 1hr to appointment)
        +-----+-----+         +-----------+
              |
      +-------+-------+
      |               |
  [complete]    [cancel by owner]
      |               |
      v               v
+-----------+   +-----------+
| COMPLETED |   | EXCEPTION |  (if < 1hr to appointment)
+-----------+   +-----+-----+
                      |
                [approve by reviewer]
                      |
                      v
                +-----------+
                |  CANCELED |
                +-----------+
```

- Max 2 reschedules per order (appointment type must match)
- Each state transition creates an immutable `OrderOperationLog` entry

## 6. Contract Lifecycle

```
INITIATED --> CONFIRMED --> SIGNED --> ARCHIVED
                                  \
                                   --> TERMINATED / VOIDED / RENEWED
```

- **INITIATED**: Created by reviewer from an ACCEPTED order with a template version
- **CONFIRMED**: User reviews and confirms terms
- **SIGNED**: User draws signature on canvas; stored as PNG with SHA-256 hash + salt
- **ARCHIVED**: Admin archives after completion
- **Display status**: Computed from persisted status + dates (ACTIVE, EXPIRING_SOON)
- **Sign page gate**: Only accessible when contract is in CONFIRMED state
- **Template rendering**: `{{fieldName}}` placeholders replaced with HTML-escaped values

## 7. Search and Ranking

### 7.1 Search Pipeline

1. User submits keyword + filters (category, condition, price range)
2. CatalogService attempts MySQL FULLTEXT search; falls back to LIKE on H2
3. Results deduplicated by SHA-256 fingerprint or normalized title
4. RankingService applies multi-signal scoring
5. SearchLog created with filters and result count
6. Click tracking via SearchClickLog linked to search session

### 7.2 Ranking Algorithm

When an active `RankingStrategyVersion` exists, items with seller metrics are scored:

```
baseScore = creditScore * creditScoreWeight
           + positiveRate * 100 * positiveRateWeight
           + reviewQuality * reviewQualityWeight
```

Additional signals:
- **Click popularity**: `+0.5 * log1p(clickCount)` (log-scaled to prevent domination)
- **Category affinity**: `+2.0` if item category matches user's completed order categories
- **Recent search recency**: `+1.0` per matching keyword in last 20 searches (max +3.0)
- **Review sentiment**: Rating-based signal (-1.5 to +2.0) scaled by review count confidence

Items below `minCreditScoreThreshold` or `minPositiveRateThreshold` are filtered out. Items without sellers are placed after ranked items.

Admin can create and activate different strategy versions to tune weights and thresholds.

## 8. Appointment System

- Configurable business hours (default 8:00-18:00), 30-minute slots
- Separate capacity for PICKUP (5) and DROPOFF (5) per slot
- Auto-generation: slots created on first request for a date
- Booking window: minimum 2 hours advance, maximum 14 days ahead
- Slot booking is atomic: `bookSlot()` increments counter, `releaseSlot()` decrements

## 9. Reviews and Appeals

### Reviews
- One review per completed order (order owner only)
- Rating 1-5, text up to 1000 characters
- Up to 5 images per review (JPG/PNG, max 3MB each, SHA-256 checksums)
- Review sentiment feeds into ranking algorithm for the seller

### Appeals
- Filed against orders or contracts
- Evidence file upload (stored with checksums)
- Resolved by reviewer/admin with outcome and reasoning (`ArbitrationOutcome`)
- Status: OPEN -> RESOLVED

## 10. Deployment

### 10.1 Docker Compose

Two services:
- **mysql**: MySQL 8.0 with custom config (ft_min_word_len=2, utf8mb4, log_bin_trust_function_creators=1)
- **app**: Multi-stage build (JDK 17 build -> JRE 17 runtime)

### 10.2 Secrets Management

- `bootstrap-secrets.sh` generates secrets on first boot (JWT key, refresh key, encryption key, user passwords)
- Secrets encrypted at rest with AES-256-CBC using a machine-derived passphrase
- `entrypoint.sh` decrypts secrets into environment variables at startup
- Never written to disk in plaintext during runtime

### 10.3 Configuration Profiles

| Profile | Database | Secrets | Use Case |
|---------|----------|---------|----------|
| (default) | MySQL localhost:3306 | Environment variables (required) | Production |
| `dev` | MySQL localhost:3306 | Hardcoded dev-only values | Local development |
| `docker` | MySQL (Docker network) | Bootstrap-generated, encrypted | Docker deployment |
| `test` | H2 in-memory | Hardcoded test values | Automated tests |

### 10.4 Bootstrap Users

| Profile | Admin | Reviewer | User |
|---------|-------|----------|------|
| Docker | Auto-generated, printed to console | Auto-generated | Auto-generated |
| Dev | `DevAdmin1!pass` | `DevReviewer1!pass` | `DevUser1!pass` |

All accounts require password reset on first login.

## 11. Error Handling

| Exception | HTTP Status | When |
|-----------|-------------|------|
| `MethodArgumentNotValidException` | 400 | @Valid fails |
| `AccessDeniedException` | 403 | @PreAuthorize fails |
| `EntityNotFoundException` | 404 | Entity lookup fails |
| `BusinessRuleException` (access denied) | 403 | Ownership check fails |
| `BusinessRuleException` (other) | 409 | Business rule violated |
| `MaxUploadSizeExceededException` | 413 | File too large |
| All others | 500 | Unexpected errors |

API responses return `{timestamp, status, error, message}`. Browser 401s redirect to `/login`.

## 12. Testing

- **332 tests** across unit, service, security, authorization, API, and web layers
- **JaCoCo gates**: 70% line / 45% branch coverage
- H2 in MySQL compatibility mode for integration tests
- MockMvc for controller/security tests
- Immutability enforcement tested via EntityManager direct manipulation
