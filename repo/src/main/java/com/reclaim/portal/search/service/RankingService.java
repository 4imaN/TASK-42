package com.reclaim.portal.search.service;

import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.entity.SellerMetrics;
import com.reclaim.portal.catalog.repository.SellerMetricsRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.entity.OrderItem;
import com.reclaim.portal.orders.repository.OrderItemRepository;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.reviews.entity.Review;
import com.reclaim.portal.reviews.repository.ReviewRepository;
import com.reclaim.portal.search.entity.RankingStrategyVersion;
import com.reclaim.portal.search.entity.SearchLog;
import com.reclaim.portal.search.repository.RankingStrategyVersionRepository;
import com.reclaim.portal.search.repository.SearchClickLogRepository;
import com.reclaim.portal.search.repository.SearchLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    private final RankingStrategyVersionRepository rankingStrategyVersionRepository;
    private final SellerMetricsRepository sellerMetricsRepository;
    private final SearchClickLogRepository clickLogRepository;
    private final SearchLogRepository searchLogRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;

    public RankingService(RankingStrategyVersionRepository rankingStrategyVersionRepository,
                          SellerMetricsRepository sellerMetricsRepository,
                          SearchClickLogRepository clickLogRepository,
                          SearchLogRepository searchLogRepository,
                          OrderRepository orderRepository,
                          OrderItemRepository orderItemRepository,
                          ReviewRepository reviewRepository) {
        this.rankingStrategyVersionRepository = rankingStrategyVersionRepository;
        this.sellerMetricsRepository = sellerMetricsRepository;
        this.clickLogRepository = clickLogRepository;
        this.searchLogRepository = searchLogRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.reviewRepository = reviewRepository;
    }

    public List<RecyclingItem> rankItems(List<RecyclingItem> items) {
        return rankItems(items, null);
    }

    public List<RecyclingItem> rankItems(List<RecyclingItem> items, Long userId) {
        Optional<RankingStrategyVersion> strategyOpt = rankingStrategyVersionRepository.findByActiveTrue();
        if (strategyOpt.isEmpty()) {
            return items;
        }

        RankingStrategyVersion strategy = strategyOpt.get();
        BigDecimal creditScoreWeight = orZero(strategy.getCreditScoreWeight());
        BigDecimal positiveRateWeight = orZero(strategy.getPositiveRateWeight());
        BigDecimal reviewQualityWeight = orZero(strategy.getReviewQualityWeight());
        BigDecimal minCreditScore = strategy.getMinCreditScoreThreshold();
        BigDecimal minPositiveRate = strategy.getMinPositiveRateThreshold();

        // Build category affinity from user's completed orders
        Set<String> affinityCategories = new HashSet<>();
        if (userId != null) {
            List<Order> completed = orderRepository.findByUserId(userId).stream()
                .filter(o -> "COMPLETED".equals(o.getOrderStatus()))
                .toList();
            for (Order o : completed) {
                orderItemRepository.findByOrderId(o.getId()).stream()
                    .map(OrderItem::getSnapshotCategory)
                    .filter(c -> c != null)
                    .forEach(affinityCategories::add);
            }
        }

        // Build recent-search keyword set from user's last 20 searches
        Set<String> recentSearchKeywords = new HashSet<>();
        if (userId != null) {
            List<SearchLog> recentSearches = searchLogRepository.findTop20ByUserIdOrderBySearchedAtDesc(userId);
            for (SearchLog sl : recentSearches) {
                if (sl.getSearchTerm() != null && !sl.getSearchTerm().isBlank()) {
                    recentSearchKeywords.add(sl.getSearchTerm().toLowerCase());
                }
            }
        }

        List<ScoredItem> qualifiedItems = new ArrayList<>();
        List<RecyclingItem> noSellerItems = new ArrayList<>();

        for (RecyclingItem item : items) {
            if (item.getSellerId() == null) {
                noSellerItems.add(item);
                continue;
            }

            Optional<SellerMetrics> metricsOpt = sellerMetricsRepository.findBySellerId(item.getSellerId());
            if (metricsOpt.isEmpty()) {
                noSellerItems.add(item);
                continue;
            }

            SellerMetrics metrics = metricsOpt.get();
            BigDecimal creditScore = orZero(metrics.getCreditScore());
            BigDecimal positiveRate = orZero(metrics.getPositiveRate());
            BigDecimal reviewQuality = orZero(metrics.getRecentReviewQuality());

            // Filter items below thresholds
            if (minCreditScore != null && creditScore.compareTo(minCreditScore) < 0) {
                continue;
            }
            if (minPositiveRate != null && positiveRate.compareTo(minPositiveRate) < 0) {
                continue;
            }

            // score = creditScore*weight + positiveRate*100*weight + recentReviewQuality*weight
            BigDecimal score = creditScore.multiply(creditScoreWeight)
                    .add(positiveRate.multiply(BigDecimal.valueOf(100)).multiply(positiveRateWeight))
                    .add(reviewQuality.multiply(reviewQualityWeight));

            // Apply local behavior signal: boost items with more clicks (log scale to avoid domination)
            long clicks = clickLogRepository.countByItemId(item.getId());
            score = score.add(BigDecimal.valueOf(Math.log1p(clicks) * 0.5));

            // Category affinity: boost items in categories the user has completed orders for
            if (affinityCategories.contains(item.getCategory())) {
                score = score.add(BigDecimal.valueOf(2.0));
            }

            // Recent-search recency boost: if item title/category matches recent search keywords
            score = score.add(computeRecentSearchBoost(item, recentSearchKeywords));

            // Review sentiment signal: adjust based on seller's review rating distribution
            score = score.add(computeReviewSentimentSignal(item.getSellerId()));

            qualifiedItems.add(new ScoredItem(item, score));
        }

        qualifiedItems.sort(Comparator.comparing(ScoredItem::score).reversed());

        // Sort no-seller items with affinity-matching ones first
        final Set<String> finalAffinityCategories = affinityCategories;
        noSellerItems.sort(Comparator.comparingInt(
            (RecyclingItem i) -> finalAffinityCategories.contains(i.getCategory()) ? 0 : 1));

        List<RecyclingItem> ranked = new ArrayList<>(qualifiedItems.size() + noSellerItems.size());
        for (ScoredItem si : qualifiedItems) {
            ranked.add(si.item());
        }
        ranked.addAll(noSellerItems);
        return ranked;
    }

    /**
     * Computes a recency boost based on whether the item's title or category
     * matches any of the user's recent search keywords. Each matching keyword
     * contributes a small boost (max 3.0 total).
     */
    public BigDecimal computeRecentSearchBoost(RecyclingItem item, Set<String> recentSearchKeywords) {
        if (recentSearchKeywords.isEmpty()) {
            return BigDecimal.ZERO;
        }

        double boost = 0.0;
        String titleLower = item.getTitle() != null ? item.getTitle().toLowerCase() : "";
        String categoryLower = item.getCategory() != null ? item.getCategory().toLowerCase() : "";

        for (String keyword : recentSearchKeywords) {
            if (titleLower.contains(keyword) || categoryLower.contains(keyword)) {
                boost += 1.0;
            }
        }

        return BigDecimal.valueOf(Math.min(boost, 3.0));
    }

    /**
     * Computes a review-sentiment signal from the seller's review rating distribution.
     * Uses the actual review data (not pre-aggregated metrics) to derive a sentiment score.
     * <ul>
     *   <li>Average rating >= 4.0: positive boost (up to +2.0)</li>
     *   <li>Average rating 3.0-4.0: neutral (0)</li>
     *   <li>Average rating < 3.0: penalty (down to -1.5)</li>
     * </ul>
     * Sellers with no reviews get no adjustment.
     */
    public BigDecimal computeReviewSentimentSignal(Long sellerId) {
        if (sellerId == null) {
            return BigDecimal.ZERO;
        }

        List<Review> reviews = reviewRepository.findReviewsForSeller(sellerId);
        if (reviews.isEmpty()) {
            return BigDecimal.ZERO;
        }

        double avgRating = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(3.0);

        long totalReviews = reviews.size();
        long positiveReviews = reviews.stream().filter(r -> r.getRating() >= 4).count();
        double positiveRatio = (double) positiveReviews / totalReviews;

        // Combined sentiment: weighted average of rating-based and distribution-based signals
        double ratingSignal;
        if (avgRating >= 4.0) {
            ratingSignal = (avgRating - 4.0) * 2.0; // 0 to 2.0 for ratings 4.0-5.0
        } else if (avgRating >= 3.0) {
            ratingSignal = 0.0;
        } else {
            ratingSignal = (avgRating - 3.0) * 1.5; // -1.5 to 0 for ratings 1.0-3.0
        }

        // Positive ratio boost: high positive ratio adds a small bonus
        double distributionSignal = (positiveRatio - 0.5) * 1.0; // -0.5 to 0.5

        // Scale by log of review count to give more weight to sellers with more reviews
        double confidenceScale = Math.min(Math.log1p(totalReviews) / 3.0, 1.0);

        return BigDecimal.valueOf((ratingSignal + distributionSignal) * confidenceScale)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record ScoredItem(RecyclingItem item, BigDecimal score) {
    }
}
