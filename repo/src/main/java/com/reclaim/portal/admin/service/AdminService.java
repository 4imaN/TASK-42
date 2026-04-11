package com.reclaim.portal.admin.service;

import com.reclaim.portal.auth.entity.AdminAccessLog;
import com.reclaim.portal.auth.repository.AdminAccessLogRepository;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.search.entity.RankingStrategyVersion;
import com.reclaim.portal.search.entity.SearchClickLog;
import com.reclaim.portal.search.entity.SearchLog;
import com.reclaim.portal.search.entity.SearchTrend;
import com.reclaim.portal.search.repository.RankingStrategyVersionRepository;
import com.reclaim.portal.search.repository.SearchClickLogRepository;
import com.reclaim.portal.search.repository.SearchLogRepository;
import com.reclaim.portal.search.repository.SearchTrendRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminService {

    /** Simple projection for top-clicked item data used in analytics view. */
    public record ClickedItemSummary(Long itemId, String itemName, long clickCount) {}

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final RankingStrategyVersionRepository strategyRepository;
    private final SearchLogRepository searchLogRepository;
    private final SearchTrendRepository searchTrendRepository;
    private final SearchClickLogRepository searchClickLogRepository;
    private final AdminAccessLogRepository accessLogRepository;
    private final RecyclingItemRepository recyclingItemRepository;

    public AdminService(RankingStrategyVersionRepository strategyRepository,
                        SearchLogRepository searchLogRepository,
                        SearchTrendRepository searchTrendRepository,
                        SearchClickLogRepository searchClickLogRepository,
                        AdminAccessLogRepository accessLogRepository,
                        RecyclingItemRepository recyclingItemRepository) {
        this.strategyRepository = strategyRepository;
        this.searchLogRepository = searchLogRepository;
        this.searchTrendRepository = searchTrendRepository;
        this.searchClickLogRepository = searchClickLogRepository;
        this.accessLogRepository = accessLogRepository;
        this.recyclingItemRepository = recyclingItemRepository;
    }

    /**
     * Creates a new (inactive) ranking strategy version.
     */
    public RankingStrategyVersion createRankingStrategy(String versionLabel,
                                                        BigDecimal creditScoreWeight,
                                                        BigDecimal positiveRateWeight,
                                                        BigDecimal reviewQualityWeight,
                                                        BigDecimal minCreditScoreThreshold,
                                                        BigDecimal minPositiveRateThreshold,
                                                        Long createdBy) {
        RankingStrategyVersion strategy = new RankingStrategyVersion();
        strategy.setVersionLabel(versionLabel);
        strategy.setCreditScoreWeight(creditScoreWeight);
        strategy.setPositiveRateWeight(positiveRateWeight);
        strategy.setReviewQualityWeight(reviewQualityWeight);
        strategy.setMinCreditScoreThreshold(minCreditScoreThreshold);
        strategy.setMinPositiveRateThreshold(minPositiveRateThreshold);
        strategy.setActive(false);
        strategy.setCreatedBy(createdBy);
        strategy.setCreatedAt(LocalDateTime.now());
        return strategyRepository.save(strategy);
    }

    /**
     * Deactivates all strategies, then activates the specified one.
     * Logs the activation in AdminAccessLog.
     */
    public RankingStrategyVersion activateStrategy(Long strategyId, Long adminUserId) {
        RankingStrategyVersion target = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new EntityNotFoundException("RankingStrategyVersion", strategyId));

        // Deactivate all existing active strategies
        List<RankingStrategyVersion> all = strategyRepository.findAllByOrderByCreatedAtDesc();
        for (RankingStrategyVersion s : all) {
            if (s.isActive()) {
                s.setActive(false);
                strategyRepository.save(s);
            }
        }

        // Activate the target
        target.setActive(true);
        RankingStrategyVersion activated = strategyRepository.save(target);

        // Log the action
        AdminAccessLog log = new AdminAccessLog();
        log.setAdminUserId(adminUserId);
        log.setActionType("STRATEGY_ACTIVATE");
        log.setTargetEntity("RankingStrategyVersion");
        log.setTargetId(strategyId);
        log.setReason("Strategy activation");
        log.setCreatedAt(LocalDateTime.now());
        accessLogRepository.save(log);

        return activated;
    }

    /**
     * Returns all ranking strategies, ordered by createdAt descending.
     */
    @Transactional(readOnly = true)
    public List<RankingStrategyVersion> getStrategies() {
        return strategyRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Returns the currently active ranking strategy, if any.
     */
    @Transactional(readOnly = true)
    public RankingStrategyVersion getActiveStrategy() {
        return strategyRepository.findByActiveTrue()
                .orElseThrow(() -> new EntityNotFoundException("No active RankingStrategyVersion found"));
    }

    /**
     * Returns search analytics:
     * - totalSearches (count)
     * - totalClicks (count)
     * - uniqueKeywords (distinct search terms count)
     * - topTerms (top 10 trends by search count)
     * - topClickedItems (top 10 items by click count)
     * - recentSearches (last 20 search log entries)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSearchAnalytics() {
        long totalSearches = searchLogRepository.count();

        List<SearchClickLog> allClicks = searchClickLogRepository.findAll();
        long totalClicks = allClicks.size();

        // Count distinct non-null search terms from trends table
        long uniqueKeywords = searchTrendRepository.count();

        List<SearchTrend> topTerms = searchTrendRepository.findTop10ByOrderBySearchCountDesc();

        // Compute top clicked items by grouping click logs, resolve item names
        Map<Long, Long> clicksPerItem = allClicks.stream()
                .filter(c -> c.getItemId() != null)
                .collect(Collectors.groupingBy(SearchClickLog::getItemId, Collectors.counting()));

        // Collect IDs for the top 10
        List<Long> topItemIds = clicksPerItem.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();

        // Batch-resolve item names
        Map<Long, String> itemNames = recyclingItemRepository.findAllById(topItemIds).stream()
                .collect(Collectors.toMap(RecyclingItem::getId, RecyclingItem::getTitle));

        List<ClickedItemSummary> topClickedItems = topItemIds.stream()
                .map(id -> new ClickedItemSummary(
                        id,
                        itemNames.getOrDefault(id, "Unknown Item #" + id),
                        clicksPerItem.get(id)))
                .collect(Collectors.toList());

        // Compute clicks with linked search sessions (clicks that have searchLogId)
        long clicksWithSearchContext = allClicks.stream()
                .filter(c -> c.getSearchLogId() != null)
                .count();

        List<SearchLog> recentSearches = searchLogRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getSearchedAt() == null && b.getSearchedAt() == null) return 0;
                    if (a.getSearchedAt() == null) return 1;
                    if (b.getSearchedAt() == null) return -1;
                    return b.getSearchedAt().compareTo(a.getSearchedAt());
                })
                .limit(20)
                .toList();

        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("totalSearches", totalSearches);
        analytics.put("totalClicks", totalClicks);
        analytics.put("clicksWithSearchContext", clicksWithSearchContext);
        analytics.put("uniqueKeywords", uniqueKeywords);
        analytics.put("topTerms", topTerms);
        analytics.put("topClickedItems", topClickedItems);
        analytics.put("recentSearches", recentSearches);

        // Daily search trend: aggregate from search_trends table
        List<Map<String, Object>> searchTrend = searchTrendRepository.findTop10ByOrderBySearchCountDesc()
                .stream()
                .map(t -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("date", t.getPeriodStart() != null ? t.getPeriodStart().toString() : "—");
                    entry.put("count", t.getSearchCount());
                    return entry;
                })
                .toList();
        analytics.put("searchTrend", searchTrend);

        return analytics;
    }

    /**
     * Returns all admin access logs ordered by createdAt descending.
     */
    @Transactional(readOnly = true)
    public List<AdminAccessLog> getAccessLogs() {
        return accessLogRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .toList();
    }
}
