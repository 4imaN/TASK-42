package com.reclaim.portal.service;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.entity.SellerMetrics;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.catalog.repository.SellerMetricsRepository;
import com.reclaim.portal.search.entity.RankingStrategyVersion;
import com.reclaim.portal.search.repository.RankingStrategyVersionRepository;
import com.reclaim.portal.search.service.RankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RankingServiceIntegrationTest {

    @Autowired
    private RankingService rankingService;

    @Autowired
    private RankingStrategyVersionRepository strategyRepository;

    @Autowired
    private SellerMetricsRepository sellerMetricsRepository;

    @Autowired
    private RecyclingItemRepository recyclingItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long highSellerId;
    private Long lowSellerId;

    @BeforeEach
    void setUp() {
        // Deactivate all existing strategies
        strategyRepository.findAllByOrderByCreatedAtDesc().forEach(s -> {
            s.setActive(false);
            strategyRepository.save(s);
        });

        // Create real users for seller FK constraints
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User highSeller = new User();
        highSeller.setUsername("high_seller_" + System.nanoTime());
        highSeller.setPasswordHash(passwordEncoder.encode("Pass1!wordXXX"));
        highSeller.setEnabled(true);
        highSeller.setLocked(false);
        highSeller.setForcePasswordReset(false);
        highSeller.setFailedAttempts(0);
        highSeller.setCreatedAt(LocalDateTime.now());
        highSeller.setUpdatedAt(LocalDateTime.now());
        highSeller.setRoles(new HashSet<>(Set.of(userRole)));
        highSeller = userRepository.save(highSeller);
        highSellerId = highSeller.getId();

        User lowSeller = new User();
        lowSeller.setUsername("low_seller_" + System.nanoTime());
        lowSeller.setPasswordHash(passwordEncoder.encode("Pass1!wordXXX"));
        lowSeller.setEnabled(true);
        lowSeller.setLocked(false);
        lowSeller.setForcePasswordReset(false);
        lowSeller.setFailedAttempts(0);
        lowSeller.setCreatedAt(LocalDateTime.now());
        lowSeller.setUpdatedAt(LocalDateTime.now());
        lowSeller.setRoles(new HashSet<>(Set.of(userRole)));
        lowSeller = userRepository.save(lowSeller);
        lowSellerId = lowSeller.getId();
    }

    @Test
    void shouldRankByScore() {
        // Create active strategy
        RankingStrategyVersion strategy = new RankingStrategyVersion();
        strategy.setVersionLabel("test-v1");
        strategy.setCreditScoreWeight(new BigDecimal("0.5"));
        strategy.setPositiveRateWeight(new BigDecimal("0.3"));
        strategy.setReviewQualityWeight(new BigDecimal("0.2"));
        strategy.setMinCreditScoreThreshold(null);
        strategy.setMinPositiveRateThreshold(null);
        strategy.setActive(true);
        strategy.setCreatedBy(highSellerId);
        strategy.setCreatedAt(LocalDateTime.now());
        strategyRepository.save(strategy);

        // High-score seller
        SellerMetrics highMetrics = new SellerMetrics();
        highMetrics.setSellerId(highSellerId);
        highMetrics.setCreditScore(new BigDecimal("900"));
        highMetrics.setPositiveRate(new BigDecimal("0.95"));
        highMetrics.setRecentReviewQuality(new BigDecimal("4.8"));
        highMetrics.setUpdatedAt(LocalDateTime.now());
        sellerMetricsRepository.save(highMetrics);

        // Low-score seller
        SellerMetrics lowMetrics = new SellerMetrics();
        lowMetrics.setSellerId(lowSellerId);
        lowMetrics.setCreditScore(new BigDecimal("200"));
        lowMetrics.setPositiveRate(new BigDecimal("0.50"));
        lowMetrics.setRecentReviewQuality(new BigDecimal("2.0"));
        lowMetrics.setUpdatedAt(LocalDateTime.now());
        sellerMetricsRepository.save(lowMetrics);

        RecyclingItem highItem = createItem("High Quality Item", highSellerId);
        RecyclingItem lowItem = createItem("Low Quality Item", lowSellerId);

        List<RecyclingItem> ranked = rankingService.rankItems(List.of(lowItem, highItem));

        // High-score seller's item should come first
        assertThat(ranked.get(0).getSellerId()).isEqualTo(highSellerId);
        assertThat(ranked.get(1).getSellerId()).isEqualTo(lowSellerId);
    }

    @Test
    void shouldFilterBelowThreshold() {
        RankingStrategyVersion strategy = new RankingStrategyVersion();
        strategy.setVersionLabel("test-threshold-v1");
        strategy.setCreditScoreWeight(new BigDecimal("0.5"));
        strategy.setPositiveRateWeight(new BigDecimal("0.3"));
        strategy.setReviewQualityWeight(new BigDecimal("0.2"));
        strategy.setMinCreditScoreThreshold(new BigDecimal("500")); // Only sellers >= 500
        strategy.setMinPositiveRateThreshold(null);
        strategy.setActive(true);
        strategy.setCreatedBy(highSellerId);
        strategy.setCreatedAt(LocalDateTime.now());
        strategyRepository.save(strategy);

        // Below threshold
        SellerMetrics belowThreshold = new SellerMetrics();
        belowThreshold.setSellerId(lowSellerId);
        belowThreshold.setCreditScore(new BigDecimal("300"));
        belowThreshold.setPositiveRate(new BigDecimal("0.80"));
        belowThreshold.setRecentReviewQuality(new BigDecimal("3.5"));
        belowThreshold.setUpdatedAt(LocalDateTime.now());
        sellerMetricsRepository.save(belowThreshold);

        RecyclingItem belowItem = createItem("Below Threshold Item", lowSellerId);

        List<RecyclingItem> ranked = rankingService.rankItems(List.of(belowItem));

        // Item below threshold should be filtered out
        assertThat(ranked).isEmpty();
    }

    @Test
    void shouldHandleNoStrategy() {
        // No active strategy configured
        RecyclingItem item1 = createItem("Item A", null);
        RecyclingItem item2 = createItem("Item B", null);

        List<RecyclingItem> ranked = rankingService.rankItems(List.of(item1, item2));

        // Without strategy, returns items in original order
        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).getTitle()).isEqualTo("Item A");
        assertThat(ranked.get(1).getTitle()).isEqualTo("Item B");
    }

    @Test
    void shouldPlaceNoSellerItemsAfterRankedItems() {
        RankingStrategyVersion strategy = new RankingStrategyVersion();
        strategy.setVersionLabel("test-noseller-v1");
        strategy.setCreditScoreWeight(new BigDecimal("0.5"));
        strategy.setPositiveRateWeight(new BigDecimal("0.3"));
        strategy.setReviewQualityWeight(new BigDecimal("0.2"));
        strategy.setMinCreditScoreThreshold(null);
        strategy.setMinPositiveRateThreshold(null);
        strategy.setActive(true);
        strategy.setCreatedBy(highSellerId);
        strategy.setCreatedAt(LocalDateTime.now());
        strategyRepository.save(strategy);

        // High seller has metrics
        SellerMetrics highMetrics = new SellerMetrics();
        highMetrics.setSellerId(highSellerId);
        highMetrics.setCreditScore(new BigDecimal("800"));
        highMetrics.setPositiveRate(new BigDecimal("0.90"));
        highMetrics.setRecentReviewQuality(new BigDecimal("4.5"));
        highMetrics.setUpdatedAt(LocalDateTime.now());
        sellerMetricsRepository.save(highMetrics);

        RecyclingItem withSeller = createItem("Has Seller", highSellerId);
        RecyclingItem noSeller = createItem("No Seller", null);

        List<RecyclingItem> ranked = rankingService.rankItems(List.of(noSeller, withSeller));

        assertThat(ranked).hasSize(2);
        // Item with seller should come first
        assertThat(ranked.get(0).getSellerId()).isEqualTo(highSellerId);
        // Item without seller should be appended at end
        assertThat(ranked.get(1).getSellerId()).isNull();
    }

    @Test
    void shouldFilterByMinPositiveRateThreshold() {
        RankingStrategyVersion strategy = new RankingStrategyVersion();
        strategy.setVersionLabel("test-posrate-v1");
        strategy.setCreditScoreWeight(new BigDecimal("0.5"));
        strategy.setPositiveRateWeight(new BigDecimal("0.3"));
        strategy.setReviewQualityWeight(new BigDecimal("0.2"));
        strategy.setMinCreditScoreThreshold(null);
        strategy.setMinPositiveRateThreshold(new BigDecimal("0.80")); // Only >= 80% positive rate
        strategy.setActive(true);
        strategy.setCreatedBy(highSellerId);
        strategy.setCreatedAt(LocalDateTime.now());
        strategyRepository.save(strategy);

        // highSeller meets threshold
        SellerMetrics highMetrics = new SellerMetrics();
        highMetrics.setSellerId(highSellerId);
        highMetrics.setCreditScore(new BigDecimal("700"));
        highMetrics.setPositiveRate(new BigDecimal("0.90"));
        highMetrics.setRecentReviewQuality(new BigDecimal("4.0"));
        highMetrics.setUpdatedAt(LocalDateTime.now());
        sellerMetricsRepository.save(highMetrics);

        // lowSeller does NOT meet threshold
        SellerMetrics lowMetrics = new SellerMetrics();
        lowMetrics.setSellerId(lowSellerId);
        lowMetrics.setCreditScore(new BigDecimal("700"));
        lowMetrics.setPositiveRate(new BigDecimal("0.60")); // below 0.80
        lowMetrics.setRecentReviewQuality(new BigDecimal("3.0"));
        lowMetrics.setUpdatedAt(LocalDateTime.now());
        sellerMetricsRepository.save(lowMetrics);

        RecyclingItem highItem = createItem("High Rate Item", highSellerId);
        RecyclingItem lowItem = createItem("Low Rate Item", lowSellerId);

        List<RecyclingItem> ranked = rankingService.rankItems(List.of(highItem, lowItem));

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).getSellerId()).isEqualTo(highSellerId);
    }

    @Test
    void shouldHandleItemWithNoSellerMetrics() {
        RankingStrategyVersion strategy = new RankingStrategyVersion();
        strategy.setVersionLabel("test-nometrics-v1");
        strategy.setCreditScoreWeight(new BigDecimal("0.5"));
        strategy.setPositiveRateWeight(new BigDecimal("0.3"));
        strategy.setReviewQualityWeight(new BigDecimal("0.2"));
        strategy.setMinCreditScoreThreshold(null);
        strategy.setMinPositiveRateThreshold(null);
        strategy.setActive(true);
        strategy.setCreatedBy(highSellerId);
        strategy.setCreatedAt(LocalDateTime.now());
        strategyRepository.save(strategy);

        // No SellerMetrics saved for highSellerId
        RecyclingItem item = createItem("No Metrics Item", highSellerId);

        List<RecyclingItem> ranked = rankingService.rankItems(List.of(item));

        // Item goes to noSellerItems bucket (no metrics found)
        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).getSellerId()).isEqualTo(highSellerId);
    }

    @Test
    void shouldHandleEmptyItemList() {
        List<RecyclingItem> ranked = rankingService.rankItems(List.of());
        assertThat(ranked).isEmpty();
    }

    @Test
    void shouldHandleNullWeightMetricsAsZero() {
        RankingStrategyVersion strategy = new RankingStrategyVersion();
        strategy.setVersionLabel("test-null-weights-v1");
        strategy.setCreditScoreWeight(null);   // null treated as 0
        strategy.setPositiveRateWeight(null);
        strategy.setReviewQualityWeight(null);
        strategy.setMinCreditScoreThreshold(null);
        strategy.setMinPositiveRateThreshold(null);
        strategy.setActive(true);
        strategy.setCreatedBy(highSellerId);
        strategy.setCreatedAt(LocalDateTime.now());
        strategyRepository.save(strategy);

        SellerMetrics metrics = new SellerMetrics();
        metrics.setSellerId(highSellerId);
        metrics.setCreditScore(new BigDecimal("500"));
        metrics.setPositiveRate(new BigDecimal("0.70"));
        metrics.setRecentReviewQuality(null);  // null recentReviewQuality
        metrics.setUpdatedAt(LocalDateTime.now());
        sellerMetricsRepository.save(metrics);

        RecyclingItem item = createItem("Null Weight Item", highSellerId);

        List<RecyclingItem> ranked = rankingService.rankItems(List.of(item));
        assertThat(ranked).hasSize(1);
    }

    @Test
    void shouldChangeRankingOrderWhenStrategyWeightsChange() {
        // Seller A: very high credit score, low positive rate
        SellerMetrics metricsA = new SellerMetrics();
        metricsA.setSellerId(highSellerId);
        metricsA.setCreditScore(new BigDecimal("900"));
        metricsA.setPositiveRate(new BigDecimal("0.50"));
        metricsA.setRecentReviewQuality(new BigDecimal("2.0"));
        metricsA.setUpdatedAt(LocalDateTime.now());
        sellerMetricsRepository.save(metricsA);

        // Seller B: low credit score, very high positive rate
        SellerMetrics metricsB = new SellerMetrics();
        metricsB.setSellerId(lowSellerId);
        metricsB.setCreditScore(new BigDecimal("100"));
        metricsB.setPositiveRate(new BigDecimal("0.99"));
        metricsB.setRecentReviewQuality(new BigDecimal("2.0"));
        metricsB.setUpdatedAt(LocalDateTime.now());
        sellerMetricsRepository.save(metricsB);

        RecyclingItem itemA = createItem("Seller A Item", highSellerId);
        RecyclingItem itemB = createItem("Seller B Item", lowSellerId);

        // Strategy 1: heavily weight credit score
        RankingStrategyVersion strategy1 = new RankingStrategyVersion();
        strategy1.setVersionLabel("credit-heavy");
        strategy1.setCreditScoreWeight(new BigDecimal("0.9000"));
        strategy1.setPositiveRateWeight(new BigDecimal("0.0500"));
        strategy1.setReviewQualityWeight(new BigDecimal("0.0500"));
        strategy1.setActive(true);
        strategy1.setCreatedBy(highSellerId);
        strategy1.setCreatedAt(LocalDateTime.now());
        strategyRepository.save(strategy1);

        List<RecyclingItem> ranked1 = rankingService.rankItems(List.of(itemA, itemB));
        // With credit-score-heavy strategy, Seller A (credit=900) should rank first
        assertThat(ranked1.get(0).getSellerId()).isEqualTo(highSellerId);
        assertThat(ranked1.get(1).getSellerId()).isEqualTo(lowSellerId);

        // Switch strategy: heavily weight positive rate instead
        strategy1.setActive(false);
        strategyRepository.save(strategy1);

        RankingStrategyVersion strategy2 = new RankingStrategyVersion();
        strategy2.setVersionLabel("posrate-heavy");
        strategy2.setCreditScoreWeight(new BigDecimal("0.0500"));
        strategy2.setPositiveRateWeight(new BigDecimal("0.9000"));
        strategy2.setReviewQualityWeight(new BigDecimal("0.0500"));
        strategy2.setActive(true);
        strategy2.setCreatedBy(highSellerId);
        strategy2.setCreatedAt(LocalDateTime.now());
        strategyRepository.save(strategy2);

        List<RecyclingItem> ranked2 = rankingService.rankItems(List.of(itemA, itemB));
        // With positive-rate-heavy strategy, Seller B (rate=0.99) should rank first
        assertThat(ranked2.get(0).getSellerId()).isEqualTo(lowSellerId);
        assertThat(ranked2.get(1).getSellerId()).isEqualTo(highSellerId);
    }

    private RecyclingItem createItem(String title, Long sellerId) {
        RecyclingItem item = new RecyclingItem();
        item.setTitle(title);
        item.setNormalizedTitle(title.toLowerCase());
        item.setCategory("METAL");
        item.setItemCondition("GOOD");
        item.setPrice(new BigDecimal("10.00"));
        item.setCurrency("USD");
        item.setSellerId(sellerId);
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return recyclingItemRepository.save(item);
    }
}
