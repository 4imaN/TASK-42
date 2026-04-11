package com.reclaim.portal.catalog.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FullTextIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FullTextIndexInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public FullTextIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Check if index already exists (MySQL-specific query)
            var result = jdbcTemplate.queryForList(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS " +
                "WHERE TABLE_NAME = 'recycling_items' AND INDEX_TYPE = 'FULLTEXT'");

            if (result.isEmpty()) {
                log.info("Creating FULLTEXT index on recycling_items(title, description)...");
                jdbcTemplate.execute(
                    "ALTER TABLE recycling_items ADD FULLTEXT INDEX ft_items_title_desc (title, description)");
                log.info("FULLTEXT index created successfully.");
            } else {
                log.debug("FULLTEXT index already exists on recycling_items.");
            }
        } catch (Exception e) {
            // Expected on H2 — full-text search falls back to LIKE
            log.info("FULLTEXT index creation skipped (not supported on this database): {}", e.getMessage());
        }
    }
}
