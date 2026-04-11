-- Full-text search support for recycling_items — shared migration marker.
-- H2 (test database) does not support FULLTEXT INDEX syntax, so the actual DDL
-- is kept in db/migration-mysql/V7__create_fulltext_index.sql which is only loaded
-- on MySQL. Additionally, FullTextIndexInitializer.java creates the index at startup
-- on MySQL as a safety net (idempotent — skips if already present).
-- On H2, the LIKE-based search fallback in RecyclingItemRepository.searchItems() is used.
SELECT 1;
