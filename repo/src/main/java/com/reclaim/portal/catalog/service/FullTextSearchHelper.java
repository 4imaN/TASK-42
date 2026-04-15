package com.reclaim.portal.catalog.service;

import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Helper component that executes the full-text search in its own independent transaction
 * (REQUIRES_NEW). This isolates the MySQL MATCH/AGAINST failure (e.g. on H2) so that it
 * does NOT contaminate the caller's transaction with a rollback-only mark.
 */
@Component
public class FullTextSearchHelper {

    private final RecyclingItemRepository recyclingItemRepository;

    public FullTextSearchHelper(RecyclingItemRepository recyclingItemRepository) {
        this.recyclingItemRepository = recyclingItemRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<RecyclingItem> searchFullText(String keyword, String category, String condition,
                                              BigDecimal minPrice, BigDecimal maxPrice) {
        return recyclingItemRepository.searchItemsFullText(keyword, category, condition, minPrice, maxPrice);
    }
}
