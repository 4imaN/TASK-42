package com.reclaim.portal.service;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.entity.SellerMetrics;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
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
import com.reclaim.portal.search.repository.SearchLogRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the new ranking signals: recent-search recency boost and review-sentiment signal.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RankingSignalsTest {

    @Autowired private RankingService rankingService;
    @Autowired private RankingStrategyVersionRepository strategyRepository;
    @Autowired private SellerMetricsRepository sellerMetricsRepository;
    @Autowired private RecyclingItemRepository recyclingItemRepository;
    @Autowired private SearchLogRepository searchLogRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long sellerId;
    private Long buyerId;

    @BeforeEach
    void setUp() {
        strategyRepository.findAllByOrderByCreatedAtDesc().forEach(s -> {
            s.setActive(false); strategyRepository.save(s);
        });

        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_USER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User seller = createUser("rank_seller_" + System.nanoTime(), userRole);
        sellerId = seller.getId();

        User buyer = createUser("rank_buyer_" + System.nanoTime(), userRole);
        buyerId = buyer.getId();

        // Active strategy with equal weights
        RankingStrategyVersion strategy = new RankingStrategyVersion();
        strategy.setVersionLabel("signal-test-v1");
        strategy.setCreditScoreWeight(new BigDecimal("0.3"));
        strategy.setPositiveRateWeight(new BigDecimal("0.3"));
        strategy.setReviewQualityWeight(new BigDecimal("0.2"));
        strategy.setActive(true);
        strategy.setCreatedBy(sellerId);
        strategy.setCreatedAt(LocalDateTime.now());
        strategyRepository.save(strategy);

        // Seller metrics
        SellerMetrics metrics = new SellerMetrics();
        metrics.setSellerId(sellerId);
        metrics.setCreditScore(new BigDecimal("700"));
        metrics.setPositiveRate(new BigDecimal("0.85"));
        metrics.setRecentReviewQuality(new BigDecimal("4.0"));
        metrics.setUpdatedAt(LocalDateTime.now());
        sellerMetricsRepository.save(metrics);
    }

    @Test
    void recentSearchBoostRanksMatchingItemsHigher() {
        // Create two items from the same seller
        RecyclingItem matchingItem = createItem("Vintage Bicycle", sellerId, "SPORTS");
        RecyclingItem nonMatchingItem = createItem("Office Chair", sellerId, "FURNITURE");

        // User recently searched for "bicycle"
        SearchLog searchLog = new SearchLog();
        searchLog.setUserId(buyerId);
        searchLog.setSearchTerm("bicycle");
        searchLog.setResultCount(5);
        searchLog.setSearchedAt(LocalDateTime.now());
        searchLogRepository.save(searchLog);

        List<RecyclingItem> ranked = rankingService.rankItems(
                List.of(nonMatchingItem, matchingItem), buyerId);

        // "Vintage Bicycle" should rank higher due to recent search boost for "bicycle"
        assertThat(ranked.get(0).getTitle()).isEqualTo("Vintage Bicycle");
    }

    @Test
    void noRecentSearchesProducesNoBoost() {
        RecyclingItem item = createItem("Test Item", sellerId, "METAL");

        // No searches for this user
        BigDecimal boost = rankingService.computeRecentSearchBoost(item, Set.of());
        assertThat(boost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recentSearchBoostIsCapped() {
        RecyclingItem item = createItem("Metal Chair Desk", sellerId, "METAL");

        // Many matching keywords
        Set<String> keywords = Set.of("metal", "chair", "desk", "furniture", "office");
        BigDecimal boost = rankingService.computeRecentSearchBoost(item, keywords);

        // Boost should be capped at 3.0
        assertThat(boost.doubleValue()).isLessThanOrEqualTo(3.0);
    }

    @Test
    void positiveReviewSentimentBoostsRanking() {
        // Create an order with items from our seller, then leave positive reviews
        RecyclingItem item = createItem("Great Product", sellerId, "ELECTRONICS");
        Order order = createCompletedOrder(buyerId, item);
        createReview(order.getId(), buyerId, 5);

        BigDecimal signal = rankingService.computeReviewSentimentSignal(sellerId);

        // Positive reviews should produce a positive signal
        assertThat(signal.doubleValue()).isGreaterThan(0);
    }

    @Test
    void negativeReviewSentimentPenalizesRanking() {
        RecyclingItem item = createItem("Poor Product", sellerId, "ELECTRONICS");
        Order order = createCompletedOrder(buyerId, item);
        createReview(order.getId(), buyerId, 1);

        BigDecimal signal = rankingService.computeReviewSentimentSignal(sellerId);

        // Negative reviews should produce a negative signal
        assertThat(signal.doubleValue()).isLessThan(0);
    }

    @Test
    void noReviewsProducesZeroSentimentSignal() {
        BigDecimal signal = rankingService.computeReviewSentimentSignal(sellerId);
        assertThat(signal).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void nullSellerIdProducesZeroSentimentSignal() {
        BigDecimal signal = rankingService.computeReviewSentimentSignal(null);
        assertThat(signal).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void mixedReviewsProduceModerateSignal() {
        RecyclingItem item1 = createItem("Item A", sellerId, "ELECTRONICS");
        RecyclingItem item2 = createItem("Item B", sellerId, "ELECTRONICS");
        Order order1 = createCompletedOrder(buyerId, item1);
        Order order2 = createCompletedOrder(buyerId, item2);
        createReview(order1.getId(), buyerId, 5); // positive
        createReview(order2.getId(), buyerId, 2); // negative

        BigDecimal signal = rankingService.computeReviewSentimentSignal(sellerId);

        // Mixed reviews: signal should be between strong positive and strong negative
        assertThat(signal.doubleValue()).isBetween(-1.5, 2.0);
    }

    // ---- helpers ----

    private User createUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode("Pass1!wordXXX"));
        u.setEnabled(true); u.setLocked(false); u.setForcePasswordReset(false);
        u.setFailedAttempts(0);
        u.setCreatedAt(LocalDateTime.now()); u.setUpdatedAt(LocalDateTime.now());
        u.setRoles(new HashSet<>(Set.of(role)));
        return userRepository.save(u);
    }

    private RecyclingItem createItem(String title, Long sellerId, String category) {
        RecyclingItem item = new RecyclingItem();
        item.setTitle(title);
        item.setNormalizedTitle(title.toLowerCase());
        item.setCategory(category);
        item.setItemCondition("GOOD");
        item.setPrice(new BigDecimal("25.00"));
        item.setCurrency("USD");
        item.setSellerId(sellerId);
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now()); item.setUpdatedAt(LocalDateTime.now());
        return recyclingItemRepository.save(item);
    }

    private Order createCompletedOrder(Long userId, RecyclingItem item) {
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderStatus("COMPLETED");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(item.getPrice());
        order.setCreatedAt(LocalDateTime.now()); order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        OrderItem oi = new OrderItem();
        oi.setOrderId(order.getId());
        oi.setItemId(item.getId());
        oi.setSnapshotTitle(item.getTitle());
        oi.setSnapshotCategory(item.getCategory());
        oi.setSnapshotCondition(item.getItemCondition());
        oi.setSnapshotPrice(item.getPrice());
        orderItemRepository.save(oi);

        return order;
    }

    private void createReview(Long orderId, Long userId, int rating) {
        Review review = new Review();
        review.setOrderId(orderId);
        review.setReviewerUserId(userId);
        review.setRating(rating);
        review.setReviewText("Test review with rating " + rating);
        review.setCreatedAt(LocalDateTime.now()); review.setUpdatedAt(LocalDateTime.now());
        reviewRepository.save(review);
    }
}
