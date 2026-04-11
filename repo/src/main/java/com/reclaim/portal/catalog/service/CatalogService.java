package com.reclaim.portal.catalog.service;

import com.reclaim.portal.catalog.entity.ItemFingerprint;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.ItemFingerprintRepository;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.search.entity.SearchClickLog;
import com.reclaim.portal.search.entity.SearchLog;
import com.reclaim.portal.search.repository.SearchClickLogRepository;
import com.reclaim.portal.search.repository.SearchLogRepository;
import com.reclaim.portal.search.service.RankingService;
import com.reclaim.portal.search.service.SearchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@Transactional
public class CatalogService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CatalogService.class);

    /** Immutable result of a search operation, bundling ranked items with the search session ID. */
    public record SearchResult(List<RecyclingItem> items, Long searchLogId) {}

    private final RecyclingItemRepository recyclingItemRepository;
    private final ItemFingerprintRepository itemFingerprintRepository;
    private final SearchLogRepository searchLogRepository;
    private final SearchClickLogRepository searchClickLogRepository;
    private final SearchService searchService;
    private final RankingService rankingService;

    public CatalogService(RecyclingItemRepository recyclingItemRepository,
                          ItemFingerprintRepository itemFingerprintRepository,
                          SearchLogRepository searchLogRepository,
                          SearchClickLogRepository searchClickLogRepository,
                          SearchService searchService,
                          RankingService rankingService) {
        this.recyclingItemRepository = recyclingItemRepository;
        this.itemFingerprintRepository = itemFingerprintRepository;
        this.searchLogRepository = searchLogRepository;
        this.searchClickLogRepository = searchClickLogRepository;
        this.searchService = searchService;
        this.rankingService = rankingService;
    }

    public SearchResult searchItems(String keyword, String category, String condition,
                                     BigDecimal minPrice, BigDecimal maxPrice, Long userId) {
        List<RecyclingItem> results;
        try {
            results = recyclingItemRepository.searchItemsFullText(keyword, category, condition, minPrice, maxPrice);
        } catch (Exception e) {
            // Fallback to LIKE search (H2 or when full-text index not available)
            results = recyclingItemRepository.searchItems(keyword, category, condition, minPrice, maxPrice);
        }

        if (keyword != null && !keyword.isBlank()) {
            searchService.updateTrends(keyword);
        }

        // Deduplicate using persisted fingerprint hashes where available,
        // falling back to normalized title + attribute composite key.
        java.util.LinkedHashSet<String> seenKeys = new java.util.LinkedHashSet<>();
        List<RecyclingItem> deduped = new java.util.ArrayList<>();
        for (RecyclingItem item : results) {
            // Prefer the stored fingerprint hash (persisted by checkDuplicate flow)
            String key = itemFingerprintRepository.findByItemId(item.getId())
                    .map(fp -> fp.getFingerprintHash())
                    .orElseGet(() -> {
                        // Fallback: compute composite key from normalized title + attributes
                        String title = item.getNormalizedTitle() != null
                                ? item.getNormalizedTitle()
                                : (item.getTitle() != null ? item.getTitle().toLowerCase().trim() : "");
                        return title + "|" + (item.getCategory() != null ? item.getCategory() : "")
                                     + "|" + (item.getItemCondition() != null ? item.getItemCondition() : "");
                    });
            if (seenKeys.add(key)) {
                deduped.add(item);
            }
        }

        List<RecyclingItem> rankedResults = rankingService.rankItems(deduped, userId);

        SearchLog searchLog = new SearchLog();
        searchLog.setUserId(userId);
        searchLog.setSearchTerm(keyword);
        searchLog.setCategoryFilter(category);
        searchLog.setConditionFilter(condition);
        searchLog.setMinPrice(minPrice);
        searchLog.setMaxPrice(maxPrice);
        searchLog.setResultCount(rankedResults.size());
        searchLog.setSearchedAt(LocalDateTime.now());
        searchLog = searchLogRepository.save(searchLog);

        return new SearchResult(rankedResults, searchLog.getId());
    }

    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return recyclingItemRepository.findDistinctCategories();
    }

    @Transactional(readOnly = true)
    public RecyclingItem getItemById(Long id) {
        return recyclingItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RecyclingItem", id));
    }

    public void logClick(Long userId, Long searchLogId, Long itemId) {
        SearchClickLog clickLog = new SearchClickLog();
        clickLog.setUserId(userId);
        clickLog.setSearchLogId(searchLogId);
        clickLog.setItemId(itemId);
        clickLog.setClickedAt(LocalDateTime.now());
        searchClickLogRepository.save(clickLog);
    }

    public String checkDuplicate(String title, String attributes) {
        String normalizedTitle = title == null ? "" : title.toLowerCase().trim().replaceAll("\\s+", " ");
        String combined = normalizedTitle + (attributes == null ? "" : attributes);
        String hash = sha256(combined);

        if (itemFingerprintRepository.findByFingerprintHash(hash).isPresent()) {
            return "EXACT_DUPLICATE";
        }

        List<RecyclingItem> sameTitle = recyclingItemRepository.findByNormalizedTitle(normalizedTitle);
        if (!sameTitle.isEmpty()) {
            return "NEAR_DUPLICATE";
        }

        return "UNIQUE";
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
