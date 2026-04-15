package com.reclaim.portal.catalog;

import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.catalog.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Explicitly tests the LIKE-search fallback that kicks in when MySQL's native
 * {@code MATCH(...) AGAINST(...)} full-text syntax is not supported by the active database
 * (i.e. H2 test profile).
 *
 * <p>This test documents the production-behavior boundary: on H2, the full-text query
 * throws a {@code BadSqlGrammarException}; {@link CatalogService#searchItems} catches it
 * and falls back to {@code searchItems} LIKE-based JPQL. On MySQL, the native query path
 * is exercised — that boundary is marked in the README as requiring manual verification
 * against a real MySQL 8 instance with the FULLTEXT index defined.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FullTextSearchFallbackTest {

    @Autowired private CatalogService catalogService;
    @Autowired private RecyclingItemRepository recyclingItemRepository;

    private Long seededItemId;

    @BeforeEach
    void setUp() {
        RecyclingItem item = new RecyclingItem();
        item.setTitle("UniqueFalltextWidget");
        item.setNormalizedTitle("uniquefalltextwidget");
        item.setDescription("Test widget for search fallback assertions");
        item.setCategory("Electronics");
        item.setItemCondition("GOOD");
        item.setPrice(new BigDecimal("12.50"));
        item.setCurrency("USD");
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item = recyclingItemRepository.save(item);
        seededItemId = item.getId();
    }

    @Test
    void likeFallbackReturnsResultsOnH2() {
        // CatalogService tries MATCH/AGAINST first, fails on H2, falls back to LIKE.
        // The test proves the LIKE path works end-to-end and returns the seeded item.
        CatalogService.SearchResult result = catalogService.searchItems(
            "UniqueFalltextWidget", null, null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.items()).extracting(RecyclingItem::getId).contains(seededItemId);
    }

    @Test
    void nativeFullTextQueryFailsOnH2DocumentingMySqlBoundary() {
        // Direct call to the native MATCH/AGAINST query — this MUST throw on H2
        // because H2 does not support FULLTEXT syntax. On MySQL it would return results.
        // This test exists to make the boundary explicit: if someone ports to a DB
        // that supports MATCH but behaves differently, this will flag the change.
        assertThatThrownBy(() ->
            recyclingItemRepository.searchItemsFullText(
                "UniqueFalltextWidget", null, null, null, null)
        ).isInstanceOf(Exception.class); // BadSqlGrammar on H2
    }

    @Test
    void likeFallbackHonoursFiltersWhenKeywordIsNull() {
        // Filter-only query (no keyword) must still find results via LIKE fallback.
        CatalogService.SearchResult result = catalogService.searchItems(
            null, "Electronics", "GOOD", new BigDecimal("1"), new BigDecimal("100"), null);

        assertThat(result.items())
            .extracting(RecyclingItem::getId)
            .contains(seededItemId);
    }
}
