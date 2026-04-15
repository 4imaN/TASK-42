# Test Coverage Audit

## Scope
- Static inspection only. No code, tests, scripts, containers, servers, builds, or package managers were run.
- API coverage denominator uses the 61 `/api/...` endpoints defined in `src/main/java/com/reclaim/portal/*/controller/*ApiController.java`.
- Non-API backend endpoint `GET /storage/**` exists in `src/main/java/com/reclaim/portal/storage/controller/StorageFileController.java` and is noted separately.
- `MockMvc` tests are still classified as `HTTP with mocking` under the strict rule because they do not use a real network transport.

## Backend Endpoint Inventory
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/change-password`
- `GET /api/users/{id}/profile`
- `POST /api/users/{id}/reveal`
- `GET /api/catalog/search`
- `GET /api/catalog/{id}`
- `POST /api/catalog/click`
- `POST /api/catalog/check-duplicate`
- `GET /api/search/autocomplete`
- `GET /api/search/trending`
- `GET /api/appointments/available`
- `POST /api/appointments/generate`
- `POST /api/orders`
- `PUT /api/orders/{id}/accept`
- `PUT /api/orders/{id}/complete`
- `PUT /api/orders/{id}/cancel`
- `PUT /api/orders/{id}/approve-cancel`
- `PUT /api/orders/{id}/reschedule`
- `GET /api/orders/my`
- `GET /api/orders/{id}`
- `POST /api/reviews`
- `POST /api/reviews/{id}/images`
- `GET /api/reviews/order/{orderId}`
- `GET /api/reviews/my`
- `POST /api/appeals`
- `POST /api/appeals/{id}/evidence`
- `PUT /api/appeals/{id}/resolve`
- `GET /api/appeals/{id}`
- `GET /api/appeals/my`
- `GET /api/reviewer/queue`
- `PUT /api/reviewer/order-items/{id}/adjust-category`
- `PUT /api/reviewer/orders/{id}/accept`
- `PUT /api/reviewer/orders/{id}/approve-cancel`
- `POST /api/reviewer/orders/{id}/initiate-contract`
- `POST /api/admin/strategies`
- `PUT /api/admin/strategies/{id}/activate`
- `GET /api/admin/strategies`
- `GET /api/admin/strategies/active`
- `GET /api/admin/analytics/search`
- `GET /api/admin/access-logs`
- `POST /api/admin/users/{id}/reveal`
- `POST /api/contracts/templates`
- `POST /api/contracts/templates/{id}/versions`
- `POST /api/contracts/templates/versions/{id}/fields`
- `GET /api/contracts/templates`
- `GET /api/contracts/templates/{id}/versions`
- `GET /api/contracts/templates/versions/{id}/fields`
- `POST /api/contracts`
- `PUT /api/contracts/{id}/confirm`
- `PUT /api/contracts/{id}/sign`
- `PUT /api/contracts/{id}/archive`
- `PUT /api/contracts/{id}/terminate`
- `PUT /api/contracts/{id}/void`
- `PUT /api/contracts/{id}/renew`
- `GET /api/contracts/my`
- `GET /api/contracts/{id}`
- `GET /api/contracts/{id}/print`
- `POST /api/contracts/{id}/evidence`
- `GET /api/contracts/{id}/evidence`

## API Test Mapping Table
### Coverage status
- All 61 API endpoints are covered by at least one exact-route HTTP-style test.
- Real no-mock HTTP coverage now exists across multiple domains, not just auth.

### True no-mock HTTP evidence
- Auth:
  - `src/test/java/com/reclaim/portal/e2e/RealHttpAuthTest.java`
  - `src/test/java/com/reclaim/portal/e2e/RealHttpChangePasswordTest.java`
  - `src/test/java/com/reclaim/portal/e2e/BrowserLoginJourneyTest.java`
  - `src/test/java/com/reclaim/portal/e2e/BrowserLoginFailurePathsTest.java`
- Catalog and search:
  - `src/test/java/com/reclaim/portal/e2e/RealHttpCatalogTest.java`
  - `src/test/java/com/reclaim/portal/e2e/RealHttpSearchTest.java`
- Orders and appointments:
  - `src/test/java/com/reclaim/portal/e2e/RealHttpAppointmentsTest.java`
  - `src/test/java/com/reclaim/portal/e2e/RealHttpOrderLifecycleTest.java`
  - `src/test/java/com/reclaim/portal/e2e/RealHttpUserJourneyTest.java`
- Reviews and appeals:
  - `src/test/java/com/reclaim/portal/e2e/RealHttpReviewLifecycleTest.java`
  - `src/test/java/com/reclaim/portal/e2e/RealHttpAppealsFlowTest.java`
  - `src/test/java/com/reclaim/portal/e2e/RealHttpReviewsAndAppealsEvidenceTest.java`
- Contracts / reviewer / admin:
  - `src/test/java/com/reclaim/portal/e2e/RealHttpContractLifecycleTest.java`
  - `src/test/java/com/reclaim/portal/e2e/RealHttpReviewerActionsTest.java`
  - `src/test/java/com/reclaim/portal/e2e/RealHttpAdminTemplateTest.java`
- User profile:
  - `src/test/java/com/reclaim/portal/e2e/RealHttpUserProfileTest.java`
- Additional mixed-domain real HTTP coverage:
  - `src/test/java/com/reclaim/portal/e2e/RealHttpApiTest.java`

### HTTP with mocking evidence
- The dedicated API contract suite still uses `MockMvc`:
  - `src/test/java/com/reclaim/portal/api/*.java`
- The older named journey file still uses `MockMvc`:
  - `src/test/java/com/reclaim/portal/e2e/UserJourneyE2ETest.java`

## API Test Classification
### 1. True No-Mock HTTP
- Present and substantial.
- Uses `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` plus `TestRestTemplate` or HtmlUnit.

### 2. HTTP with Mocking
- Present.
- Main sources:
  - `src/test/java/com/reclaim/portal/api/*.java`
  - `src/test/java/com/reclaim/portal/security/*.java`
  - `src/test/java/com/reclaim/portal/e2e/UserJourneyE2ETest.java`

### 3. Non-HTTP
- Present.
- Main sources:
  - `src/test/java/com/reclaim/portal/service/*.java`
  - `src/test/java/com/reclaim/portal/unit/*.java`
  - `src/test/java/com/reclaim/portal/catalog/*.java`

## Mock Detection Rules
### Direct mocks/stubs detected
- `src/test/java/com/reclaim/portal/unit/SecuritySecretsValidatorTest.java`
  - mocks `Environment`
  - unit-only scope

### No API-path DI override pattern detected
- No `@MockBean` / `@SpyBean` evidence was found in the API contract suite.

## Coverage Summary
- Total API endpoints: `61`
- API endpoints with HTTP tests: `61`
- API endpoints with true no-mock HTTP coverage: `materially broad across auth, catalog, search, appointments, orders, reviews, appeals, contracts, reviewer, admin, and profile flows`
- HTTP coverage: `100%`
- True API coverage: `PASS-level`

## Unit Test Summary
### Modules clearly covered directly
- `src/test/java/com/reclaim/portal/auth/service/CustomUserDetailsServiceTest.java`
- `src/test/java/com/reclaim/portal/common/config/WebConfigTest.java`
- `src/test/java/com/reclaim/portal/common/config/ReclaimPropertiesTest.java`
- `src/test/java/com/reclaim/portal/ReclaimPortalApplicationTest.java`

### Other covered areas
- Controllers: API and page controllers are covered.
- Services: major domain services are covered through service integration, API, and real-HTTP tests.
- Auth/guards/middleware: covered via security tests and real-HTTP tests.

### Residual observation
- Repository classes still do not appear to have many dedicated repository-only tests, but this is not a fail-level gap given the breadth of integration coverage.

## API Observability Check
- Strong overall.
- Route, request shape, and response shape are visible in:
  - `src/test/java/com/reclaim/portal/api/*.java`
  - `src/test/java/com/reclaim/portal/e2e/RealHttp*.java`
- Security observability improved:
  - `src/test/java/com/reclaim/portal/security/AuthenticationTest.java`
  - `src/test/java/com/reclaim/portal/security/JwtFilterIntegrationTest.java`
  now include response-body assertions for some cases.

## Tests Check
### Success paths
- Strong.
- Real HTTP multi-step journeys now exist:
  - `src/test/java/com/reclaim/portal/e2e/RealHttpUserJourneyTest.java`
  - `src/test/java/com/reclaim/portal/e2e/RealHttpContractLifecycleTest.java`

### Failure paths
- Strong.
- Real HTTP and MockMvc negative-path coverage both exist.

### Edge cases
- Strong.

### Auth / permissions
- Strong.

### Integration boundaries
- Strong.
- There is now substantial real transport coverage in addition to service/database integration.

### `run_tests.sh`
- Docker-based and compliant.

## End-to-End Expectations
- Project type is fullstack.
- Real FE↔BE coverage exists through:
  - HtmlUnit browser tests
  - real-HTTP API journey tests
- Residual weakness:
  - the repo still keeps a parallel `MockMvc` “E2E” file (`src/test/java/com/reclaim/portal/e2e/UserJourneyE2ETest.java`), which is weaker than the newer real-HTTP journey tests.

## Test Coverage Score (0–100)
`93/100`

## Score Rationale
- Exact-route API coverage is complete.
- Real no-mock HTTP coverage is now broad enough to pass comfortably.
- Direct tests now exist for previously missing config/app/auth-service modules.
- Remaining deduction:
  - the overall strategy is mixed, with a large retained `MockMvc` suite alongside the newer real-HTTP suite.

## Key Gaps
- Real-HTTP coverage is broad, but not yet the sole dominant strategy.
- `src/test/java/com/reclaim/portal/e2e/UserJourneyE2ETest.java` still exists as a MockMvc “E2E” test and is weaker than the strict naming suggests.

## Confidence & Assumptions
- Confidence: `high`
- Assumption: a broad cross-domain real-HTTP suite is sufficient for a `PASS` even if some parallel `MockMvc` coverage remains.

# README Audit

## Project Type Detection
- Explicitly declared in `README.md`:
  - `Project type: fullstack`

## README Location
- Present at `README.md`

## Hard Gates
### Formatting
- Pass

### Startup Instructions
- Pass
- Evidence:
  - `docker-compose up` appears in the main startup path.

### Access Method
- Pass
- Evidence:
  - explicit `http://localhost:8080`

### Verification Method
- Pass
- Evidence:
  - concrete login and workflow steps are documented.

### Environment Rules
- Pass
- Main run/test path is Docker-based.
- MySQL integration instructions are also Docker-based.

### Demo Credentials
- Pass
- Explicit credentials for admin, reviewer, and user are documented.

## High Priority Issues
- None at fail level.

## Medium Priority Issues
- The configuration table still references values “provided by `dev` profile for local use”, which is slightly noisier than a pure Docker-only operator README.

## Low Priority Issues
- The README still references older MockMvc journey coverage in one sentence, even though stronger real-HTTP journey coverage now exists.

## Engineering Quality
- Tech stack clarity: strong
- Architecture explanation: strong
- Testing instructions: strong
- Security/roles explanation: strong
- Presentation quality: strong

## README Verdict
`PASS`

## README Score
`96/100`

## README Rationale
- All major strict gates now pass.
- Remaining deductions are polish-level, not compliance-level.

# Final Verdict

- Test Coverage Audit: `PASS`
- README Audit: `PASS`
- Combined Audit: `PASS`

## Overall Score
`94/100`

