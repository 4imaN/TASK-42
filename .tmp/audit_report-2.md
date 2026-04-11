# Static Audit Report: ReClaim Lease & Recycling Operations Portal

## 1. Verdict
- Overall conclusion: `Partial Pass`

## 2. Scope and Static Verification Boundary
- Reviewed: repository structure, README/configuration, Spring Boot entry/security configuration, controllers/services/entities/repositories, Flyway migrations, Thymeleaf templates, static assets, test sources, and generated JaCoCo report. Evidence includes [README.md](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/README.md#L1), [pom.xml](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/pom.xml), [SecurityConfig.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/common/config/SecurityConfig.java#L1).
- Not reviewed: runtime behavior, browser rendering, actual DB execution, Docker boot behavior, external integrations.
- Intentionally not executed: project startup, tests, Docker, external services, browser flows.
- Manual verification required for: actual offline runtime flows, real MySQL full-text behavior, JWT cookie behavior in-browser, template rendering fidelity, print flow, and production secret/bootstrap behavior.

## 3. Repository / Requirement Mapping Summary
- Prompt core goal: an offline Spring Boot + Thymeleaf recycling and lease portal covering user order/search/review flows, reviewer queue and contract initiation, admin template/strategy/analytics management, local-file evidence retention, JWT auth, RBAC, MySQL persistence, immutable operation logs, and recommendation/search ranking.
- Main mapped implementation areas: auth/security (`auth`, `common/config`), catalog/search/ranking (`catalog`, `search`), order/appointment workflow (`orders`, `appointments`), reviews (`reviews`), contracts/appeals/storage (`contracts`, `appeals`, `storage`), admin/reviewer/user Thymeleaf pages (`templates/*`), and migrations (`db/migration/*`).

## 4. Section-by-section Review

### 1. Hard Gates

#### 1.1 Documentation and static verifiability
- Conclusion: `Partial Pass`
- Rationale: startup and test instructions exist and are mostly consistent with the codebase, but the documented local-dev path does not document how a reviewer obtains bootstrap users outside Docker, which weakens static verifiability for manual acceptance.
- Evidence: [README.md](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/README.md#L15), [README.md](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/README.md#L38), [BootstrapService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/service/BootstrapService.java#L59), [application-dev.yml](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/application-dev.yml#L1)
- Manual verification note: verify whether local non-Docker reviewers are expected to seed users manually.

#### 1.2 Material deviation from the Prompt
- Conclusion: `Partial Pass`
- Rationale: the project is centered on the requested business domain, but one core admin capability, seller-metric-driven ranking visibility, is not meaningfully exercisable from the delivered portal because seeded catalog items have no sellers and no in-app seller-metrics management path was found.
- Evidence: [README.md](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/README.md#L109), [V5__seed_catalog.sql](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/db/migration/V5__seed_catalog.sql#L1), [RankingService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/search/service/RankingService.java#L107), [CatalogApiController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/catalog/controller/CatalogApiController.java#L31)

### 2. Delivery Completeness

#### 2.1 Core requirements coverage
- Conclusion: `Partial Pass`
- Rationale: most core flows are implemented statically: search/filtering, trending/autocomplete, appointment validation, order state machine, reviewer queue, reviews with image upload, contract lifecycle, appeals, admin templates/strategies, local file storage, and JWT/RBAC. Gaps remain around enforceable immutability of operation logs and end-to-end exercisability of seller-based ranking controls.
- Evidence: [README.md](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/README.md#L111), [AppointmentServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/AppointmentServiceIntegrationTest.java#L95), [OrderServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/OrderServiceIntegrationTest.java#L140), [ContractServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/ContractServiceIntegrationTest.java#L145), [V3__orders_and_appointments.sql](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/db/migration/V3__orders_and_appointments.sql#L55)

#### 2.2 End-to-end deliverable vs partial example
- Conclusion: `Pass`
- Rationale: this is a complete multi-module application with docs, migrations, templates, APIs, services, tests, and packaging, not a code fragment or teaching-only sample.
- Evidence: [README.md](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/README.md#L1), [pom.xml](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/pom.xml), [src/main/resources/templates/user/search.html](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/templates/user/search.html), [src/main/resources/db/migration/V1__core_users_and_auth.sql](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/db/migration/V1__core_users_and_auth.sql#L1)

### 3. Engineering and Architecture Quality

#### 3.1 Structure and module decomposition
- Conclusion: `Pass`
- Rationale: modules are separated by domain with conventional controller/service/repository/entity layering; security and storage concerns are centralized rather than piled into a single file.
- Evidence: [SecurityConfig.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/common/config/SecurityConfig.java#L1), [OrderService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/orders/service/OrderService.java), [ContractService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/contracts/service/ContractService.java), [StorageFileController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/storage/controller/StorageFileController.java#L1)

#### 3.2 Maintainability and extensibility
- Conclusion: `Partial Pass`
- Rationale: overall structure is maintainable, but some prompt-sensitive areas are hard-coded or under-constrained: reviewer/user category lists are fixed in page controllers, and operation-log immutability is only a convention.
- Evidence: [UserPageController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/users/controller/UserPageController.java#L77), [ReviewerPageController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/reviewer/controller/ReviewerPageController.java#L55), [ReviewerService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/reviewer/service/ReviewerService.java#L54), [OrderOperationLogRepository.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/orders/repository/OrderOperationLogRepository.java#L9)

### 4. Engineering Details and Professionalism

#### 4.1 Error handling, logging, validation, API design
- Conclusion: `Partial Pass`
- Rationale: validation and error handling are generally present, and services log meaningful events, but account-state enforcement is incomplete in auth, and file-integrity verification does not cover review-image checksum records.
- Evidence: [GlobalExceptionHandler.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/common/exception/GlobalExceptionHandler.java), [AuthService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/service/AuthService.java#L52), [ReviewService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/reviews/service/ReviewService.java#L99), [StorageFileController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/storage/controller/StorageFileController.java#L221)
- Manual verification note: confirm whether log sinks or HTTP responses expose any sensitive values at runtime.

#### 4.2 Real product/service vs demo shape
- Conclusion: `Pass`
- Rationale: the deliverable has the shape of a real application, including migrations, auth, RBAC, storage, templates, APIs, tests, Docker files, and admin/reviewer/user areas.
- Evidence: [README.md](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/README.md#L80), [docker-compose.yml](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/docker-compose.yml), [AdminApiController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/admin/controller/AdminApiController.java), [templates/admin/analytics.html](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/templates/admin/analytics.html)

### 5. Prompt Understanding and Requirement Fit

#### 5.1 Business-goal and constraint fit
- Conclusion: `Partial Pass`
- Rationale: the codebase reflects strong understanding of the offline recycling + lease-contract workflow, but two prompt-critical constraints are weakened: account disable/lock semantics are not reliably enforced through token issuance/consumption, and admin ranking controls are not fully realizable in the shipped portal data path.
- Evidence: [README.md](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/README.md#L91), [AuthService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/service/AuthService.java#L56), [JwtAuthenticationFilter.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/filter/JwtAuthenticationFilter.java#L68), [RankingService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/search/service/RankingService.java#L107)

### 6. Aesthetics

#### 6.1 Visual and interaction quality
- Conclusion: `Cannot Confirm Statistically`
- Rationale: templates and CSS indicate a deliberate UI system, but actual rendering, responsiveness, hover states, image display, and browser behavior were not executed per audit boundary.
- Evidence: [layouts/default.html](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/templates/layouts/default.html), [styles.css](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/static/css/styles.css), [contract/sign.html](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/templates/contract/sign.html#L1)
- Manual verification note: visual QA in browser is required.

## 5. Issues / Suggestions (Severity-Rated)

### High
- Severity: `High`
- Title: Account disable/lock state is not consistently enforced for JWT login and request authentication
- Conclusion: `Fail`
- Evidence: [AuthService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/service/AuthService.java#L56), [AuthService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/service/AuthService.java#L99), [JwtAuthenticationFilter.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/filter/JwtAuthenticationFilter.java#L68), [CustomUserDetailsService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/service/CustomUserDetailsService.java#L27)
- Impact: disabled users can still receive tokens if credentials match, and users with previously issued valid JWTs can continue to access protected routes despite account disablement; this weakens a core security boundary for local RBAC.
- Minimum actionable fix: enforce `enabled` and current lock status in `AuthService.login`, `AuthService.refresh`, and `JwtAuthenticationFilter` before authenticating or minting tokens; add explicit negative tests for disabled and post-lock token usage.

- Severity: `High`
- Title: Admin ranking-strategy capability is not meaningfully exercisable in the shipped portal data path
- Conclusion: `Partial Fail`
- Evidence: [V5__seed_catalog.sql](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/db/migration/V5__seed_catalog.sql#L1), [RankingService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/search/service/RankingService.java#L107), [CatalogApiController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/catalog/controller/CatalogApiController.java#L31)
- Impact: one of the prompt’s explicit admin responsibilities, controlling recommendation visibility using seller credit score, positive rate, and review quality, cannot be validated end-to-end from the delivered portal without direct database manipulation.
- Minimum actionable fix: ship seller-linked catalog data plus a supported admin or seed path for seller metrics, and add an integration/UI-level test proving strategy changes alter ranked results.

### Medium
- Severity: `Medium`
- Title: Immutable order-operation logs are not enforced as immutable
- Conclusion: `Fail`
- Evidence: [V3__orders_and_appointments.sql](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/db/migration/V3__orders_and_appointments.sql#L55), [OrderOperationLog.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/orders/entity/OrderOperationLog.java#L42), [OrderOperationLogRepository.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/orders/repository/OrderOperationLogRepository.java#L10)
- Impact: the prompt requires immutable operation logs, but schema, entity, and repository design all permit later mutation or deletion.
- Minimum actionable fix: make logs append-only by contract and storage, for example remove mutators from service exposure, use insert-only repository patterns, and add DB protections or auditing constraints.

- Severity: `Medium`
- Title: Local-development acceptance path lacks documented bootstrap-user provisioning
- Conclusion: `Partial Fail`
- Evidence: [README.md](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/README.md#L15), [README.md](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/README.md#L38), [BootstrapService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/service/BootstrapService.java#L60), [application-dev.yml](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/application-dev.yml#L1)
- Impact: reviewers following the documented local path may have no credentials to access role-based flows without reverse-engineering bootstrap behavior.
- Minimum actionable fix: document the non-Docker bootstrap path or provide dev-profile seeded users explicitly.

- Severity: `Medium`
- Title: Review-image checksum storage is not matched by retrieval-time verification
- Conclusion: `Partial Fail`
- Evidence: [V4__contracts_reviews_appeals.sql](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/db/migration/V4__contracts_reviews_appeals.sql#L108), [ReviewService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/reviews/service/ReviewService.java#L119), [StorageFileController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/storage/controller/StorageFileController.java#L221)
- Impact: review images are stored with checksums but served without checksum lookup, weakening evidence integrity for a dispute-resolution feature.
- Minimum actionable fix: include `review_images` in checksum resolution and add a failing test for tampered review-image retrieval.

- Severity: `Medium`
- Title: Contract sign page can present a misleading workflow state
- Conclusion: `Partial Fail`
- Evidence: [contract/sign.html](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/resources/templates/contract/sign.html#L40), [UserPageController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/users/controller/UserPageController.java#L207), [ContractService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/contracts/service/ContractService.java#L222)
- Impact: users can be shown a signing screen labeled `CONFIRMED` even when the backend would reject signing, which weakens the prompt’s requirement for a clear step-by-step contract flow.
- Minimum actionable fix: gate the sign page on confirmed status and bind the badge text to actual contract state.

- Severity: `Medium`
- Title: Reviewer recategorization UI is coupled to a fixed category list
- Conclusion: `Partial Fail`
- Evidence: [UserPageController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/users/controller/UserPageController.java#L84), [ReviewerPageController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/reviewer/controller/ReviewerPageController.java#L61), [ReviewerService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/reviewer/service/ReviewerService.java#L54)
- Impact: category adjustment is less extensible than the business workflow suggests, and newly introduced categories cannot propagate naturally through the portal.
- Minimum actionable fix: source categories from persisted catalog/admin configuration rather than hard-coded page-controller lists.

### Low
- Severity: `Low`
- Title: Repository includes generated build/test artifacts
- Conclusion: `Partial Fail`
- Evidence: [target/site/jacoco/jacoco.csv](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/target/site/jacoco/jacoco.csv#L1)
- Impact: source delivery is noisier than necessary and blurs reviewed source vs generated evidence.
- Minimum actionable fix: remove generated build outputs from source delivery and keep them in CI artifacts instead.

## 6. Security Review Summary
- Authentication entry points: `Partial Pass`. Local login, refresh, logout, JWT, and lockout logic exist, but disabled-account enforcement is not consistently applied. Evidence: [AuthApiController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/controller/AuthApiController.java), [AuthService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/service/AuthService.java#L52).
- Route-level authorization: `Pass`. URL rules restrict admin/reviewer routes and default to authenticated access. Evidence: [SecurityConfig.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/common/config/SecurityConfig.java#L55).
- Object-level authorization: `Partial Pass`. Ownership checks exist for orders, contracts, appeals, and reviews, but the account-status flaw weakens the trust boundary for already-issued credentials. Evidence: [AuthorizationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/AuthorizationTest.java#L216), [ReviewOwnershipTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/ReviewOwnershipTest.java#L130).
- Function-level authorization: `Pass`. Method security is enabled and sensitive reviewer/admin operations use role checks. Evidence: [SecurityConfig.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/common/config/SecurityConfig.java#L20), [ContractInitiationAuthTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/ContractInitiationAuthTest.java).
- Tenant / user isolation: `Partial Pass`. Static evidence supports owner scoping for key records, but runtime/browser verification was not performed. Evidence: [AuthorizationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/AuthorizationTest.java#L204), [StorageFileController.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/storage/controller/StorageFileController.java#L200).
- Admin / internal / debug protection: `Pass`. Admin paths are role-protected and no obvious debug endpoints were found in reviewed scope. Evidence: [SecurityConfig.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/common/config/SecurityConfig.java#L66), [AuthenticationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/AuthenticationTest.java#L86).

## 7. Tests and Logging Review
- Unit tests: `Partial Pass`. There are many service-focused tests, but some high-risk negative cases are absent, especially disabled-account auth paths. Evidence: [AuthServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/AuthServiceIntegrationTest.java#L87), [AppointmentServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/AppointmentServiceIntegrationTest.java#L31), [rg results](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/AuthServiceIntegrationTest.java#L114)
- API / integration tests: `Partial Pass`. Auth, order, review, contract, authorization, and storage tests exist, but controller coverage is weak in some admin/search/catalog areas. Evidence: [AuthenticationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/AuthenticationTest.java#L71), [AuthorizationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/AuthorizationTest.java#L204), [jacoco.csv](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/target/site/jacoco/jacoco.csv#L14), [jacoco.csv](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/target/site/jacoco/jacoco.csv#L30), [jacoco.csv](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/target/site/jacoco/jacoco.csv#L45)
- Logging categories / observability: `Pass`. Services use structured SLF4J logging for auth and contract lifecycle events rather than ad hoc prints. Evidence: [AuthService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/service/AuthService.java#L29), [ContractService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/contracts/service/ContractService.java#L218)
- Sensitive-data leakage risk in logs / responses: `Partial Pass`. No obvious password/token logging was found in reviewed code, but runtime output was not executed and some auth success/failure logging includes usernames and IPs. Evidence: [AuthService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/service/AuthService.java#L62), [AuthService.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/main/java/com/reclaim/portal/auth/service/AuthService.java#L79)

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- Unit/service and API/integration tests exist under `src/test/java`, using Spring Boot Test, MockMvc, JUnit 5, and AssertJ. Evidence: [AuthenticationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/AuthenticationTest.java#L25), [AppointmentServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/AppointmentServiceIntegrationTest.java#L20)
- Test entry points are documented as `./run_tests.sh` and `./mvnw clean verify`. Evidence: [README.md](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/README.md#L51)
- Generated coverage evidence exists in JaCoCo output. Evidence: [jacoco.csv](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/target/site/jacoco/jacoco.csv#L1)

### 8.2 Coverage Mapping Table
| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Login, lockout, password rules | [AuthServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/AuthServiceIntegrationTest.java#L87) | successful login, 5-failure lockout, locked login rejection, password complexity | basically covered | no disabled-account case; no refresh rejection for disabled user | add tests for `enabled=false` login and refresh rejection |
| Unauthenticated 401 and wrong-role 403 | [AuthenticationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/AuthenticationTest.java#L71) | `/api/orders/my` 401, `/api/admin/strategies` 403 | sufficient | no test that disabled/locked JWT user is denied | add request-auth tests with disabled/locked persisted user |
| Object-level order/contract/appeal isolation | [AuthorizationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/AuthorizationTest.java#L216) | user A/B fixtures, direct repository setup, forbidden cross-user access | sufficient | none obvious in reviewed scope | add storage-owner positive path for linked evidence/signature files |
| Appointment window rules | [AppointmentServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/AppointmentServiceIntegrationTest.java#L95) | min-advance, max-advance, capacity, slot generation | sufficient | runtime UI feedback not proven | add controller-level validation response tests |
| Order state machine, reschedule/cancel rules, logs | [OrderServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/OrderServiceIntegrationTest.java#L140) | create/accept/complete/cancel/reschedule/log retrieval | basically covered | immutability of operation logs is not tested | add test proving logs cannot be updated/deleted through supported paths |
| Review ownership and image limits | [ReviewOwnershipTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/ReviewOwnershipTest.java#L130), [ReviewServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/ReviewServiceIntegrationTest.java) | owner/non-owner review creation, image upload rules | basically covered | checksum verification for review images untested | add tamper-detection test on served review images |
| Contract lifecycle and status derivation | [ContractServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/ContractServiceIntegrationTest.java#L145), [ContractSignaturePrintTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/ContractSignaturePrintTest.java) | initiate/confirm/sign/archive/status assertions | basically covered | sign-page gating and badge fidelity are not tested | add page/controller test for sign-page access only when confirmed |
| Search trends/autocomplete/recommendation logic | [SearchServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/SearchServiceIntegrationTest.java#L29), [SearchClickContextTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/SearchClickContextTest.java) | trend increments, autocomplete suggestions, click context | insufficient | no end-to-end proof that admin strategy changes affect shipped seller-linked results | add integration test with seller metrics + active strategy + ranked search assertions |
| Storage access control and traversal defense | [StorageFileControllerTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/web/StorageFileControllerTest.java#L51) | admin access, unauthenticated 401, unowned signature 403, traversal rejection | basically covered | linked owner-access and review-image integrity paths are not covered | add tests for owner access to linked files and checksum mismatch rejection |

### 8.3 Security Coverage Audit
- Authentication: `Insufficient`. Tests cover happy-path login, lockout, and unauthenticated/bad-token handling, but not disabled-account or disabled-after-token cases. Evidence: [AuthServiceIntegrationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/AuthServiceIntegrationTest.java#L97), [AuthenticationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/AuthenticationTest.java#L78)
- Route authorization: `Basically covered`. Admin/reviewer gating has direct MockMvc coverage. Evidence: [AuthenticationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/AuthenticationTest.java#L86)
- Object-level authorization: `Covered for major records`. Orders, contracts, appeals, and reviews have explicit cross-user denial tests. Evidence: [AuthorizationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/AuthorizationTest.java#L216), [ReviewOwnershipTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/service/ReviewOwnershipTest.java#L131)
- Tenant / data isolation: `Partially covered`. Core ownership checks are tested, but storage-owner linked-file access and seller-metric admin visibility are not deeply exercised. Evidence: [StorageFileControllerTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/web/StorageFileControllerTest.java#L69)
- Admin / internal protection: `Basically covered`. Wrong-role admin access is tested, but controller coverage for some admin endpoints remains low. Evidence: [AuthenticationTest.java](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/src/test/java/com/reclaim/portal/security/AuthenticationTest.java#L86), [jacoco.csv](/Users/aimanmengesha/Desktop/eagle point/Slopering/w2t42/repo/target/site/jacoco/jacoco.csv#L45)

### 8.4 Final Coverage Judgment
- `Partial Pass`
- Major risks covered: baseline auth, lockout, many RBAC/object-ownership flows, appointment constraints, order transitions, contract lifecycle, and storage traversal protection.
- Major uncovered risks: disabled-account enforcement, token validity after account state changes, immutable-log guarantees, review-image integrity verification, and end-to-end admin ranking strategy effectiveness. Because of those gaps, existing tests could still pass while severe defects remain.

## 9. Final Notes
- This report is static-only and does not claim runtime success.
- The strongest acceptance risk is security-account-state enforcement, followed by the incomplete end-to-end proof of admin ranking controls.
- Manual verification should focus on disabled-user behavior, seller-metric-driven ranking in a realistic dataset, sign-page workflow gating, and file-integrity behavior for review images.
