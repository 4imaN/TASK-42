# questions.md
## Business Logic Questions Log

### 1. What does "fully offline" mean for this Spring Boot + Thymeleaf portal?
1. **What sounded ambiguous:** The prompt says the system must remain fully offline, but it does not spell out whether that only means no internet dependency or also no cloud storage, no third-party auth, no hosted search, and no external signature service.
2. **How it was understood:** Offline means the whole product must run on a local machine or LAN using only local services and bundled assets.
3. **How it was solved:** Keep everything local: Spring Boot, MySQL, JWT auth, Thymeleaf templates, search, analytics, contract storage, evidence storage, and signature artifacts. Do not rely on SaaS APIs, SMTP, cloud buckets, or hosted search.

### 2. Where does the recyclable item catalog come from in an offline deployment?
1. **What sounded ambiguous:** Users can search items by keyword, category, condition, and price range, but the prompt does not say whether the item list is remote, uploaded, or preloaded.
2. **How it was understood:** The repo needs a local catalog that works immediately on first boot and can still be maintained locally afterward.
3. **How it was solved:** Seed a starter catalog from local seed data and expose reviewer or admin maintenance flows for the canonical catalog.

### 3. How should autocomplete and trending searches update from local usage?
1. **What sounded ambiguous:** The prompt requires autocomplete and trending panels that update from local usage, but it does not define whether that is per-user, global, synchronous, or batch-updated.
2. **How it was understood:** The simplest prompt-faithful behavior is local analytics generated from repository-contained search and click activity.
3. **How it was solved:** Log searches and clicks locally, maintain suggestion and trending aggregates in MySQL, and drive the UI from those local aggregates.

### 4. Where do seller credit score, positive rate, and recent review quality come from in an offline system?
1. **What sounded ambiguous:** The prompt says ranking and recommendation visibility depend on seller credit score, positive rate, and recent review quality, but it does not define whether those values are imported, manually maintained, or derived internally.
2. **How it was understood:** These values must be fully local and auditable.
3. **How it was solved:** Store seller scoring attributes in local tables. Positive rate and recent review quality are computed from local transaction and review data; seller credit score is an internal locally maintained field with admin governance and audit logs.

### 5. How should appointment availability be calculated?
1. **What sounded ambiguous:** The prompt requires 30-minute appointment windows with validation rules, but it does not define business hours, daily capacity, or whether pickup and drop-off share the same slot inventory.
2. **How it was understood:** Availability should be deterministic, locally configurable, and enforced both in the UI and on the backend.
3. **How it was solved:** Use local configurable business hours and per-slot capacity. Generate 30-minute windows, enforce the 2-hour minimum and 14-day maximum server-side and client-side, and track pickup and drop-off capacity separately.

### 6. How do reschedules and cancellations interact with the order state machine?
1. **What sounded ambiguous:** The prompt says orders have controlled states, can be rescheduled up to two times, and may require reviewer approval for late cancellation, but it does not define whether rescheduling mutates the order state, the appointment record, or both.
2. **How it was understood:** A single order should remain stable while appointment details change under audit.
3. **How it was solved:** Keep one order identity, version the appointment details, count reschedules on the order, and log every change immutably. Late cancellations become approval requests instead of direct cancellation.

### 7. What can a reviewer change when adjusting item categorization?
1. **What sounded ambiguous:** Reviewers may adjust item categorization, but it is not clear whether they are editing the canonical catalog, the transaction snapshot, or both.
2. **How it was understood:** Reviewers need to correct the transaction immediately without silently rewriting the shared catalog.
3. **How it was solved:** Store an item snapshot on each order. Reviewer edits update the transaction snapshot; canonical catalog changes remain a separate controlled flow.

### 8. How should contract templates and contract instances be versioned?
1. **What sounded ambiguous:** The prompt requires templates, template versions, clause field definitions, and contract instances, but it does not say whether an in-flight order should inherit template edits made later.
2. **How it was understood:** Contract instances must remain legally and operationally stable once initiated.
3. **How it was solved:** Version templates explicitly and copy the selected template version plus rendered fields onto the contract instance at initiation time.

### 9. What form should signature capture take in an offline browser-based system?
1. **What sounded ambiguous:** The prompt requires a signature capture panel, but it does not define whether signatures are typed names, drawn signatures, uploads, or external e-signature events.
2. **How it was understood:** The best offline fit is local drawn signature capture in the browser.
3. **How it was solved:** Use a browser canvas or pointer-based signature panel, persist the rendered artifact locally, and store a checksum or hash for integrity verification.

### 10. How should review images and evidence attachments be stored and validated?
1. **What sounded ambiguous:** The prompt says reviews can include up to five images and contracts can retain evidence attachments locally, but it does not define storage layout, duplicate handling, or integrity rules.
2. **How it was understood:** All user-provided files must be validated before storage and remain auditable after storage.
3. **How it was solved:** Store files on local disk under a controlled storage service, validate file type, extension, count, and size limits before write, and persist checksums and metadata in MySQL.

### 11. How should reviewer PII masking and admin reveal work?
1. **What sounded ambiguous:** The prompt says reviewer views mask PII by default and admins can explicitly reveal it with access logging, but it does not define how much is masked or how reveal events are recorded.
2. **How it was understood:** Reviewers should always receive masked values by default, and admins must perform an explicit audited reveal action.
3. **How it was solved:** Return masked DTOs to reviewer screens and APIs. Provide a dedicated admin reveal action that records actor, timestamp, reason, target record, and fields revealed.

### 12. How should ranking and recommendation display strategies be configured?
1. **What sounded ambiguous:** Administrators manage display strategies based on seller credit score, positive rate, and recent review quality, but the prompt does not define whether these are fixed rules, weights, thresholds, or on/off visibility switches.
2. **How it was understood:** Admins need a transparent local strategy model rather than hard-coded ranking behavior.
3. **How it was solved:** Store versioned strategy configurations with weights, thresholds, and visibility rules. The active strategy drives ranking and recommendation visibility, and all changes are audit logged.

### 13. How should appeals and arbitration outcomes relate to orders and contracts?
1. **What sounded ambiguous:** The prompt lists appeals and arbitration outcomes in persistence requirements, but it does not place them clearly in the end-to-end flow.
2. **How it was understood:** Appeals and arbitration are post-transaction dispute artifacts tied back to the original transaction and its evidence.
3. **How it was solved:** Model appeals as separate case records linked to orders, optional contract instances, reviews, and evidence files, with arbitration outcomes stored locally.

### 14. How should MySQL full-text search and deduplication work together?
1. **What sounded ambiguous:** The prompt requires MySQL full-text search plus deduplication by normalized item title and attribute fingerprints, but it does not define whether dedupe blocks inserts, flags candidates, or only affects ranking.
2. **How it was understood:** Search should stay broad, while dedupe should stop clear duplicates and surface near-duplicates for controlled review.
3. **How it was solved:** Use MySQL full-text indexes for retrieval, normalized title and fingerprint fields for duplicate checks, hard-block exact duplicates, and queue near-duplicates for reviewer confirmation.

### 15. How should JWT secrets and encryption material be handled if there must be no manual environment input?
1. **What sounded ambiguous:** The prompt requires secure token handling and salted or encrypted sensitive fields, while the delivery constraints require a stand-alone startup with no manual env setup.
2. **How it was understood:** The repo cannot require a human to create secrets before first run, but secrets still must not be committed.
3. **How it was solved:** Generate local runtime secrets on first container boot, persist them in a Docker volume, and reuse them on later restarts.

### 16. How should first-use local accounts be handled if the stack must start stand-alone?
1. **What sounded ambiguous:** The prompt defines local auth and three roles, but it does not explain how a fresh offline installation should gain initial access without manual provisioning steps.
2. **How it was understood:** A fresh clone must still be operable without hidden setup work.
3. **How it was solved:** Seed one bootstrap account for each role on first boot, generate strong random initial passwords, persist them once in local runtime storage, and force password reset on first successful login.

### 17. What counts as "expiring soon" for contract status?
1. **What sounded ambiguous:** The prompt requires contract statuses including active, expiring soon, renewed, terminated, and voided, but it does not define the threshold for "expiring soon."
2. **How it was understood:** The system needs a default threshold that is predictable and still configurable.
3. **How it was solved:** Use a local configurable threshold with a default of 30 days before contract end date, and derive the status server-side.

### 18. Which supporting implementation pieces are necessary, and which would be prompt drift?
1. **What sounded ambiguous:** The repo needs startup, testing, storage, and bootstrap mechanics that are not spelled out in the business prompt, but adding too much convenience infrastructure would dilute the delivery.
2. **How it was understood:** Only implementation details that directly enable startup, security, persistence, auditability, or testing should be added.
3. **How it was solved:** Keep Docker bootstrapping, Flyway, local seed data, runtime secret generation, storage volumes, and the test harness. Exclude unrelated repository automation, cloud integrations, and convenience tooling that does not directly support the prompt.
