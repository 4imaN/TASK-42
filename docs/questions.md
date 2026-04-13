## Business Logic Questions Log (Simplified Format)

### 1. What does "fully offline" mean?
- **Problem:** Does offline mean no internet only, or also no cloud, APIs, or external services?
- **Interpretation:** Everything must run locally on a machine or LAN.
- **Solution:** Use only local services (Spring Boot, MySQL, JWT, storage, etc.), no external dependencies. :contentReference[oaicite:0]{index=0}

---

### 2. Where does the recyclable item catalog come from?
- **Problem:** Source of catalog not defined (remote vs local).
- **Interpretation:** Must work immediately offline.
- **Solution:** Seed local data and allow admin maintenance.

---

### 3. How do autocomplete and trending searches update?
- **Problem:** Not clear how updates happen (per user, global, batch).
- **Interpretation:** Use local activity data.
- **Solution:** Log searches/clicks locally and generate suggestions from MySQL.

---

### 4. Where do seller scores and ratings come from?
- **Problem:** Not defined if imported or computed.
- **Interpretation:** Must be locally generated and auditable.
- **Solution:** Compute from local transactions/reviews; store in local tables.

---

### 5. How is appointment availability calculated?
- **Problem:** Business hours, capacity, and rules unclear.
- **Interpretation:** Must be configurable and deterministic.
- **Solution:** Use local configs, 30-minute slots, enforce constraints server-side.

---

### 6. How do reschedules and cancellations affect orders?
- **Problem:** Not clear if order or appointment changes.
- **Interpretation:** Order stays constant, appointment evolves.
- **Solution:** Version appointments, track reschedules, log all changes.

---

### 7. What can reviewers edit in item categorization?
- **Problem:** Catalog vs transaction edits unclear.
- **Interpretation:** Fix transaction without changing catalog.
- **Solution:** Store item snapshots per order; edit only snapshot.

---

### 8. How are contract templates versioned?
- **Problem:** Do updates affect active contracts?
- **Interpretation:** Contracts must remain stable.
- **Solution:** Version templates; copy into contract at creation.

---

### 9. How should signature capture work?
- **Problem:** Format of signatures unclear.
- **Interpretation:** Must work offline in browser.
- **Solution:** Use canvas-based drawn signatures stored locally.

---

### 10. How are review images and attachments handled?
- **Problem:** Storage and validation unclear.
- **Interpretation:** Must be secure and auditable.
- **Solution:** Store locally, validate files, track metadata and checksums.

---

### 11. How does PII masking and admin reveal work?
- **Problem:** Masking level and logging unclear.
- **Interpretation:** Default masked, explicit reveal by admin.
- **Solution:** Mask in APIs; log all reveal actions with audit details.

---

### 12. How are ranking and recommendations configured?
- **Problem:** Rules not defined (weights, thresholds, etc.).
- **Interpretation:** Needs flexible admin control.
- **Solution:** Store configurable strategies with audit logging.

---

### 13. How do appeals and arbitration fit in?
- **Problem:** Placement in workflow unclear.
- **Interpretation:** Post-transaction dispute handling.
- **Solution:** Link appeals to orders, contracts, and evidence.

---

### 14. How do search and deduplication work together?
- **Problem:** Whether dedupe blocks or just flags.
- **Interpretation:** Search broad, dedupe controlled.
- **Solution:** Block exact duplicates; flag near-duplicates for review.

---

### 15. How are JWT secrets handled without setup?
- **Problem:** Security vs no manual config conflict.
- **Interpretation:** Must auto-generate securely.
- **Solution:** Generate secrets on first run and persist locally.

---

### 16. How are first-use accounts created?
- **Problem:** No initial access defined.
- **Interpretation:** Must work out-of-the-box.
- **Solution:** Seed default accounts; force password reset on first login.

---

### 17. What counts as "expiring soon"?
- **Problem:** No defined threshold.
- **Interpretation:** Needs default and configurable value.
- **Solution:** Default 30 days; configurable locally.

---

### 18. What implementation details are necessary?
- **Problem:** Risk of adding too much extra tooling.
- **Interpretation:** Only essential features should be included.
- **Solution:** Keep core setup (Docker, Flyway, storage, tests); avoid unnecessary extras.
