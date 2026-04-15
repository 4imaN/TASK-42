package com.reclaim.portal.catalog;

import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL-specific integration test that actually exercises the native
 * {@code MATCH(...) AGAINST(...)} full-text path against a real MySQL 8
 * instance, closing the production-behavior gap that the H2 fallback
 * cannot validate.
 *
 * <p>Runs only when the environment variable {@code RUN_MYSQL_IT=true} is set
 * (or equivalent), so the default `./mvnw test` run does not require Docker.
 * CI pipelines that include Docker can flip the flag to exercise this suite.
 * Testcontainers downloads the MySQL image automatically on first run.
 *
 * <p>Why gated: Testcontainers needs a running Docker daemon; making it run
 * unconditionally would break environments without Docker. The gate is
 * explicit and documented in the README.
 */
@SpringBootTest
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_IT", matches = "true")
class MySqlFullTextSearchIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("reclaim_portal")
            .withUsername("reclaim")
            .withPassword("reclaim");

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        // Use the MySQL-only migration path too so V7 (FULLTEXT INDEX) runs here.
        registry.add("spring.flyway.locations",
            () -> "classpath:db/migration,classpath:db/migration-mysql");
    }

    @Autowired private RecyclingItemRepository itemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        // Ensure the FULLTEXT index exists (V7 creates it, but be idempotent)
        try {
            jdbcTemplate.execute(
                "ALTER TABLE recycling_items ADD FULLTEXT INDEX ft_items_title_desc (title, description)");
        } catch (Exception ignored) {
            // Index already exists from V7 migration
        }

        RecyclingItem a = new RecyclingItem();
        a.setTitle("Vintage Wooden Chair");
        a.setNormalizedTitle("vintage wooden chair");
        a.setDescription("Solid oak dining chair");
        a.setCategory("Furniture");
        a.setItemCondition("GOOD");
        a.setPrice(new BigDecimal("45.00"));
        a.setCurrency("USD");
        a.setActive(true);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(a);

        RecyclingItem b = new RecyclingItem();
        b.setTitle("Modern Plastic Stool");
        b.setNormalizedTitle("modern plastic stool");
        b.setDescription("Lightweight stackable seat");
        b.setCategory("Furniture");
        b.setItemCondition("LIKE_NEW");
        b.setPrice(new BigDecimal("15.00"));
        b.setCurrency("USD");
        b.setActive(true);
        b.setCreatedAt(LocalDateTime.now());
        b.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(b);
    }

    @Test
    void nativeMatchAgainstMatchesTitleOnMySql() {
        List<RecyclingItem> results = itemRepository.searchItemsFullText(
            "chair", null, null, null, null);
        assertThat(results)
            .extracting(RecyclingItem::getTitle)
            .contains("Vintage Wooden Chair");
    }

    @Test
    void nativeMatchAgainstMatchesDescriptionOnMySql() {
        List<RecyclingItem> results = itemRepository.searchItemsFullText(
            "stackable", null, null, null, null);
        assertThat(results)
            .extracting(RecyclingItem::getTitle)
            .contains("Modern Plastic Stool");
    }

    @Test
    void nativeMatchAgainstHonoursCategoryFilter() {
        List<RecyclingItem> results = itemRepository.searchItemsFullText(
            "chair", "Furniture", null, null, null);
        assertThat(results).hasSizeGreaterThanOrEqualTo(1);
        assertThat(results).allMatch(i -> "Furniture".equals(i.getCategory()));
    }

    @Test
    void nativeMatchAgainstHonoursPriceFilter() {
        List<RecyclingItem> results = itemRepository.searchItemsFullText(
            null, "Furniture", null, new BigDecimal("20"), new BigDecimal("50"));
        assertThat(results).extracting(RecyclingItem::getTitle)
            .contains("Vintage Wooden Chair")
            .doesNotContain("Modern Plastic Stool");
    }
}
