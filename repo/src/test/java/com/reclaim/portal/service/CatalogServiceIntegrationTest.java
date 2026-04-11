package com.reclaim.portal.service;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.catalog.entity.ItemFingerprint;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.ItemFingerprintRepository;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.catalog.service.CatalogService;
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
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogServiceIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private RecyclingItemRepository recyclingItemRepository;

    @Autowired
    private ItemFingerprintRepository itemFingerprintRepository;

    @Autowired
    private SearchLogRepository searchLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private RecyclingItem metalItem;
    private RecyclingItem plasticItem;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User testUser = new User();
        testUser.setUsername("catalog_user_" + System.nanoTime());
        testUser.setPasswordHash(passwordEncoder.encode("Pass1!wordXXX"));
        testUser.setEnabled(true);
        testUser.setLocked(false);
        testUser.setForcePasswordReset(false);
        testUser.setFailedAttempts(0);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
        testUser.setRoles(new HashSet<>(Set.of(role)));
        testUser = userRepository.save(testUser);
        testUserId = testUser.getId();

        metalItem = new RecyclingItem();
        metalItem.setTitle("Aluminum Cans");
        metalItem.setNormalizedTitle("aluminum cans");
        metalItem.setDescription("Recycled aluminum cans in good condition");
        metalItem.setCategory("METAL");
        metalItem.setItemCondition("GOOD");
        metalItem.setPrice(new BigDecimal("5.00"));
        metalItem.setCurrency("USD");
        metalItem.setActive(true);
        metalItem.setCreatedAt(LocalDateTime.now());
        metalItem.setUpdatedAt(LocalDateTime.now());
        metalItem = recyclingItemRepository.save(metalItem);

        plasticItem = new RecyclingItem();
        plasticItem.setTitle("PET Bottles");
        plasticItem.setNormalizedTitle("pet bottles");
        plasticItem.setDescription("Used PET plastic bottles");
        plasticItem.setCategory("PLASTIC");
        plasticItem.setItemCondition("FAIR");
        plasticItem.setPrice(new BigDecimal("2.00"));
        plasticItem.setCurrency("USD");
        plasticItem.setActive(true);
        plasticItem.setCreatedAt(LocalDateTime.now());
        plasticItem.setUpdatedAt(LocalDateTime.now());
        plasticItem = recyclingItemRepository.save(plasticItem);
    }

    @Test
    void shouldSearchByKeyword() {
        List<RecyclingItem> results = catalogService.searchItems("Aluminum", null, null, null, null, testUserId).items();

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(r -> r.getTitle().contains("Aluminum"));
    }

    @Test
    void shouldFilterByCategory() {
        List<RecyclingItem> results = catalogService.searchItems(null, "PLASTIC", null, null, null, testUserId).items();

        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(r -> "PLASTIC".equals(r.getCategory()));
    }

    @Test
    void shouldLogSearch() {
        long beforeCount = searchLogRepository.count();

        catalogService.searchItems("recycling", null, null, null, null, testUserId);

        long afterCount = searchLogRepository.count();
        assertThat(afterCount).isGreaterThan(beforeCount);
    }

    @Test
    void shouldCheckDuplicateReturnsUnique() {
        String result = catalogService.checkDuplicate("Brand New Item XYZ", "color=red");

        assertThat(result).isEqualTo("UNIQUE");
    }

    @Test
    void shouldCheckDuplicateReturnsNearDuplicate() {
        // "aluminum cans" is already in normalizedTitle
        String result = catalogService.checkDuplicate("Aluminum Cans", null);

        assertThat(result).isEqualTo("NEAR_DUPLICATE");
    }

    @Test
    void shouldCheckExactDuplicate() {
        // Create a fingerprint with matching hash
        String title = "exact duplicate item";
        String combined = title + "attr=value";

        // Compute SHA-256 manually to create a matching fingerprint
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String hash = java.util.HexFormat.of().formatHex(hashBytes);

            ItemFingerprint fp = new ItemFingerprint();
            fp.setFingerprintHash(hash);
            fp.setItemId(metalItem.getId());
            fp.setCreatedAt(LocalDateTime.now());
            itemFingerprintRepository.save(fp);

            String result = catalogService.checkDuplicate("Exact Duplicate Item", "attr=value");
            assertThat(result).isEqualTo("EXACT_DUPLICATE");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldGetItemById() {
        RecyclingItem found = catalogService.getItemById(metalItem.getId());

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(metalItem.getId());
        assertThat(found.getTitle()).isEqualTo("Aluminum Cans");
    }

    @Test
    void shouldLogClickEvent() {
        // searchClickLog has FK on user_id, search_log_id, item_id
        // Pass null for user and searchLogId to avoid FK violations on optional fields
        // Actually search_click_logs user_id FK allows null? No it doesn't per migration
        // Just pass the testUserId with no search_log_id (it's nullable)
        catalogService.logClick(testUserId, null, metalItem.getId());
        // No assertion needed beyond no-exception
    }

    @Test
    void shouldFilterByCondition() {
        List<RecyclingItem> results = catalogService.searchItems(null, null, "FAIR", null, null, testUserId).items();

        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(r -> "FAIR".equals(r.getItemCondition()));
    }

    @Test
    void shouldFilterByMinPrice() {
        List<RecyclingItem> results = catalogService.searchItems(null, null, null,
            new BigDecimal("4.00"), null, testUserId).items();

        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(r -> r.getPrice().compareTo(new BigDecimal("4.00")) >= 0);
    }

    @Test
    void shouldFilterByMaxPrice() {
        List<RecyclingItem> results = catalogService.searchItems(null, null, null,
            null, new BigDecimal("3.00"), testUserId).items();

        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(r -> r.getPrice().compareTo(new BigDecimal("3.00")) <= 0);
    }

    @Test
    void shouldFilterByPriceRange() {
        // Only plasticItem (2.00) falls in 1.00..3.00
        List<RecyclingItem> results = catalogService.searchItems(null, null, null,
            new BigDecimal("1.00"), new BigDecimal("3.00"), testUserId).items();

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(r -> r.getId().equals(plasticItem.getId()));
        assertThat(results).noneMatch(r -> r.getId().equals(metalItem.getId()));
    }

    @Test
    void shouldReturnEmptyWhenNoMatchForKeyword() {
        List<RecyclingItem> results = catalogService.searchItems(
            "XYZNONEXISTENTTERM", null, null, null, null, testUserId).items();

        assertThat(results).isEmpty();
    }

    @Test
    void shouldGetItemByIdThrowsForUnknownId() {
        long nonexistentId = 999999L;
        assertThatThrownBy(() -> catalogService.getItemById(nonexistentId))
            .isInstanceOf(com.reclaim.portal.common.exception.EntityNotFoundException.class);
    }

    @Test
    void shouldSearchWithAllFilters() {
        List<RecyclingItem> results = catalogService.searchItems(
            "Aluminum", "METAL", "GOOD", new BigDecimal("1.00"), new BigDecimal("10.00"), testUserId).items();

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(r -> r.getId().equals(metalItem.getId()));
    }

    @Test
    void shouldLogClickWithSearchLogId() {
        // First create a search log entry to reference
        long before = searchLogRepository.count();
        catalogService.searchItems("aluminum", null, null, null, null, testUserId);
        long after = searchLogRepository.count();
        assertThat(after).isGreaterThan(before);

        com.reclaim.portal.search.entity.SearchLog lastLog = searchLogRepository.findAll()
            .stream()
            .max(java.util.Comparator.comparing(com.reclaim.portal.search.entity.SearchLog::getSearchedAt))
            .orElseThrow();

        // Log click referencing the search log
        catalogService.logClick(testUserId, lastLog.getId(), metalItem.getId());
    }
}
