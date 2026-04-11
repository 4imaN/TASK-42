package com.reclaim.portal.service;

import com.reclaim.portal.admin.service.AdminService;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.search.entity.RankingStrategyVersion;
import com.reclaim.portal.search.entity.SearchTrend;
import com.reclaim.portal.search.repository.RankingStrategyVersionRepository;
import com.reclaim.portal.search.repository.SearchTrendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminServiceIntegrationTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private RankingStrategyVersionRepository strategyRepository;

    @Autowired
    private SearchTrendRepository searchTrendRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long adminUserId;

    @BeforeEach
    void setUp() {
        // Create a real admin user for FK constraints
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_ADMIN");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User adminUser = new User();
        adminUser.setUsername("admin_svc_" + System.nanoTime());
        adminUser.setPasswordHash(passwordEncoder.encode("AdminPass1!"));
        adminUser.setEnabled(true);
        adminUser.setLocked(false);
        adminUser.setForcePasswordReset(false);
        adminUser.setFailedAttempts(0);
        adminUser.setCreatedAt(LocalDateTime.now());
        adminUser.setUpdatedAt(LocalDateTime.now());
        adminUser.setRoles(new HashSet<>(Set.of(adminRole)));
        adminUser = userRepository.save(adminUser);
        adminUserId = adminUser.getId();

        // Deactivate all existing strategies to avoid interference
        strategyRepository.findAllByOrderByCreatedAtDesc().forEach(s -> {
            s.setActive(false);
            strategyRepository.save(s);
        });
    }

    @Test
    void shouldCreateStrategy() {
        RankingStrategyVersion strategy = adminService.createRankingStrategy(
            "v-test-1",
            new BigDecimal("0.5"),
            new BigDecimal("0.3"),
            new BigDecimal("0.2"),
            new BigDecimal("400"),
            new BigDecimal("0.7"),
            adminUserId
        );

        assertThat(strategy).isNotNull();
        assertThat(strategy.getId()).isNotNull();
        assertThat(strategy.getVersionLabel()).isEqualTo("v-test-1");
        assertThat(strategy.isActive()).isFalse();
    }

    @Test
    void shouldActivateStrategy() {
        RankingStrategyVersion strategy = adminService.createRankingStrategy(
            "v-activate-test",
            new BigDecimal("0.4"),
            new BigDecimal("0.4"),
            new BigDecimal("0.2"),
            null,
            null,
            adminUserId
        );

        RankingStrategyVersion activated = adminService.activateStrategy(strategy.getId(), adminUserId);

        assertThat(activated.isActive()).isTrue();
        assertThat(activated.getId()).isEqualTo(strategy.getId());
    }

    @Test
    void shouldGetAnalytics() {
        // Seed a trend
        SearchTrend trend = new SearchTrend();
        trend.setSearchTerm("analytics-test-term");
        trend.setSearchCount(42);
        trend.setLastSearchedAt(LocalDateTime.now());
        trend.setPeriodStart(LocalDate.now());
        trend.setPeriodEnd(LocalDate.now());
        searchTrendRepository.save(trend);

        Map<String, Object> analytics = adminService.getSearchAnalytics();

        assertThat(analytics).containsKey("totalSearches");
        assertThat(analytics).containsKey("topTerms");
        assertThat(analytics).containsKey("recentSearches");
        assertThat((Long) analytics.get("totalSearches")).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void shouldGetStrategies() {
        adminService.createRankingStrategy(
            "v-list-test",
            new BigDecimal("0.5"),
            new BigDecimal("0.3"),
            new BigDecimal("0.2"),
            null, null, adminUserId
        );

        var strategies = adminService.getStrategies();
        assertThat(strategies).isNotEmpty();
    }

    @Test
    void shouldGetAccessLogs() {
        // Activate a strategy to create an access log entry
        RankingStrategyVersion strategy = adminService.createRankingStrategy(
            "v-log-test", new BigDecimal("0.5"), new BigDecimal("0.3"), new BigDecimal("0.2"),
            null, null, adminUserId
        );
        adminService.activateStrategy(strategy.getId(), adminUserId);

        var logs = adminService.getAccessLogs();
        assertThat(logs).isNotEmpty();
    }
}
