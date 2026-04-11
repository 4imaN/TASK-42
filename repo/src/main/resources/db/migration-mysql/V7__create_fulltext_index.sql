-- MySQL 8 full-text index on recycling_items.
-- This migration file lives in db/migration-mysql/ which is included in the Flyway
-- locations for the MySQL profile (application.yml) but NOT for the H2 test profile
-- (application-test.yml). H2 does not support FULLTEXT INDEX syntax.
--
-- The FullTextIndexInitializer ApplicationRunner also attempts to create this index
-- at startup and is idempotent — it checks INFORMATION_SCHEMA.STATISTICS before
-- issuing the ALTER TABLE. Whichever runs first succeeds; the second is a no-op.
ALTER TABLE recycling_items ADD FULLTEXT INDEX ft_items_title_desc (title, description);
