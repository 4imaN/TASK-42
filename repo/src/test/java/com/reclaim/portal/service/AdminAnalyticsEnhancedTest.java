package com.reclaim.portal.service;

import com.reclaim.portal.admin.service.AdminService;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.catalog.service.CatalogService;
import com.reclaim.portal.search.entity.SearchClickLog;
import com.reclaim.portal.search.entity.SearchLog;
import com.reclaim.portal.search.repository.SearchClickLogRepository;
import com.reclaim.portal.search.repository.SearchLogRepository;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for enhanced admin analytics: item names in top clicked items,
 * search session linkage tracking, and single-record click semantics.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminAnalyticsEnhancedTest {

    @Autowired private AdminService adminService;
    @Autowired private CatalogService catalogService;
    @Autowired private RecyclingItemRepository recyclingItemRepository;
    @Autowired private SearchClickLogRepository clickLogRepository;
    @Autowired private SearchLogRepository searchLogRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long userId;
    private RecyclingItem testItem;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_USER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User user = new User();
        user.setUsername("analytics_test_" + System.nanoTime());
        user.setPasswordHash(passwordEncoder.encode("Pass1!wordXXX"));
        user.setEnabled(true); user.setLocked(false); user.setForcePasswordReset(false);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>(Set.of(userRole)));
        user = userRepository.save(user);
        userId = user.getId();

        testItem = new RecyclingItem();
        testItem.setTitle("Copper Wire Bundle");
        testItem.setNormalizedTitle("copper wire bundle");
        testItem.setCategory("METAL");
        testItem.setItemCondition("GOOD");
        testItem.setPrice(new BigDecimal("35.00"));
        testItem.setCurrency("USD");
        testItem.setSellerId(userId);
        testItem.setActive(true);
        testItem.setCreatedAt(LocalDateTime.now()); testItem.setUpdatedAt(LocalDateTime.now());
        testItem = recyclingItemRepository.save(testItem);
    }

    @Test
    void topClickedItemsShouldIncludeItemNames() {
        // Create click log entries
        SearchClickLog click = new SearchClickLog();
        click.setUserId(userId);
        click.setItemId(testItem.getId());
        click.setClickedAt(LocalDateTime.now());
        clickLogRepository.save(click);

        Map<String, Object> analytics = adminService.getSearchAnalytics();

        @SuppressWarnings("unchecked")
        List<AdminService.ClickedItemSummary> topClicked =
                (List<AdminService.ClickedItemSummary>) analytics.get("topClickedItems");

        assertThat(topClicked).isNotEmpty();

        AdminService.ClickedItemSummary summary = topClicked.stream()
                .filter(s -> s.itemId().equals(testItem.getId()))
                .findFirst()
                .orElse(null);

        assertThat(summary).isNotNull();
        assertThat(summary.itemName()).isEqualTo("Copper Wire Bundle");
        assertThat(summary.clickCount()).isEqualTo(1L);
    }

    @Test
    void analyticsIncludeClicksWithSearchContextCount() {
        // Create a search log
        SearchLog searchLog = new SearchLog();
        searchLog.setUserId(userId);
        searchLog.setSearchTerm("copper");
        searchLog.setResultCount(1);
        searchLog.setSearchedAt(LocalDateTime.now());
        searchLog = searchLogRepository.save(searchLog);

        // Click WITH search context
        SearchClickLog clickWithContext = new SearchClickLog();
        clickWithContext.setUserId(userId);
        clickWithContext.setItemId(testItem.getId());
        clickWithContext.setSearchLogId(searchLog.getId());
        clickWithContext.setClickedAt(LocalDateTime.now());
        clickLogRepository.save(clickWithContext);

        // Click WITHOUT search context
        SearchClickLog clickNoContext = new SearchClickLog();
        clickNoContext.setUserId(userId);
        clickNoContext.setItemId(testItem.getId());
        clickNoContext.setSearchLogId(null);
        clickNoContext.setClickedAt(LocalDateTime.now());
        clickLogRepository.save(clickNoContext);

        Map<String, Object> analytics = adminService.getSearchAnalytics();

        assertThat((Long) analytics.get("totalClicks")).isGreaterThanOrEqualTo(2L);
        assertThat((Long) analytics.get("clicksWithSearchContext")).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void singleClickProducesSingleRecord() {
        long countBefore = clickLogRepository.count();

        catalogService.logClick(userId, null, testItem.getId());

        long countAfter = clickLogRepository.count();
        assertThat(countAfter - countBefore).isEqualTo(1);
    }
}
