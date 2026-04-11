package com.reclaim.portal.service;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.catalog.service.CatalogService;
import com.reclaim.portal.search.entity.SearchClickLog;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that search sessions produce a searchLogId that can be linked to click events,
 * and that clicks carrying searchLogId contribute to analytics context counts.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SearchClickContextTest {

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
        user.setUsername("click_ctx_" + System.nanoTime());
        user.setPasswordHash(passwordEncoder.encode("Pass1!wordXXX"));
        user.setEnabled(true); user.setLocked(false); user.setForcePasswordReset(false);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>(Set.of(userRole)));
        user = userRepository.save(user);
        userId = user.getId();

        testItem = new RecyclingItem();
        testItem.setTitle("Search Context Test Item");
        testItem.setNormalizedTitle("search context test item");
        testItem.setCategory("METAL");
        testItem.setItemCondition("GOOD");
        testItem.setPrice(new BigDecimal("10.00"));
        testItem.setCurrency("USD");
        testItem.setActive(true);
        testItem.setCreatedAt(LocalDateTime.now()); testItem.setUpdatedAt(LocalDateTime.now());
        testItem = recyclingItemRepository.save(testItem);
    }

    @Test
    void searchReturnsSearchLogId() {
        CatalogService.SearchResult result = catalogService.searchItems(
                "Search", null, null, null, null, userId);

        assertThat(result.searchLogId()).isNotNull();
        assertThat(result.items()).isNotNull();
    }

    @Test
    void searchLogIdCanBeUsedToLinkClickEvent() {
        CatalogService.SearchResult result = catalogService.searchItems(
                "Search", null, null, null, null, userId);
        Long searchLogId = result.searchLogId();

        // Simulate a click with the search session context
        catalogService.logClick(userId, searchLogId, testItem.getId());

        // Verify the click was stored with the search session link
        List<SearchClickLog> clicks = clickLogRepository.findAll();
        SearchClickLog linkedClick = clicks.stream()
                .filter(c -> searchLogId.equals(c.getSearchLogId())
                           && testItem.getId().equals(c.getItemId()))
                .findFirst()
                .orElse(null);

        assertThat(linkedClick).isNotNull();
        assertThat(linkedClick.getSearchLogId()).isEqualTo(searchLogId);
        assertThat(linkedClick.getUserId()).isEqualTo(userId);
    }

    @Test
    void clickWithSearchContextCountsInAnalytics() {
        // Perform search to get searchLogId
        CatalogService.SearchResult result = catalogService.searchItems(
                "Context", null, null, null, null, userId);
        Long searchLogId = result.searchLogId();

        // Log click with search context
        catalogService.logClick(userId, searchLogId, testItem.getId());

        // Log click without search context
        catalogService.logClick(userId, null, testItem.getId());

        // Verify counts
        List<SearchClickLog> allClicks = clickLogRepository.findAll();
        long withContext = allClicks.stream()
                .filter(c -> c.getSearchLogId() != null)
                .count();
        long withoutContext = allClicks.stream()
                .filter(c -> c.getSearchLogId() == null)
                .count();

        assertThat(withContext).isGreaterThanOrEqualTo(1);
        assertThat(withoutContext).isGreaterThanOrEqualTo(1);
    }

    @Test
    void eachSearchProducesDistinctSearchLogId() {
        CatalogService.SearchResult result1 = catalogService.searchItems(
                "First", null, null, null, null, userId);
        CatalogService.SearchResult result2 = catalogService.searchItems(
                "Second", null, null, null, null, userId);

        assertThat(result1.searchLogId()).isNotEqualTo(result2.searchLogId());
    }

    @Test
    void singleClickProducesSingleRecord() {
        long countBefore = clickLogRepository.count();

        CatalogService.SearchResult result = catalogService.searchItems(
                "Single", null, null, null, null, userId);
        catalogService.logClick(userId, result.searchLogId(), testItem.getId());

        long countAfter = clickLogRepository.count();
        assertThat(countAfter - countBefore).isEqualTo(1);
    }
}
