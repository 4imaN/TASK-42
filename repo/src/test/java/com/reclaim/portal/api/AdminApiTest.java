package com.reclaim.portal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.AdminAccessLogRepository;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.search.entity.RankingStrategyVersion;
import com.reclaim.portal.search.repository.RankingStrategyVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private RankingStrategyVersionRepository strategyRepository;
    @Autowired private AdminAccessLogRepository adminAccessLogRepository;

    private User adminUser;
    private User regularUser;
    private User targetUser;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role adminRole = findOrCreateRole("ROLE_ADMIN");
        Role userRole  = findOrCreateRole("ROLE_USER");

        adminUser   = createUser("admin_api_admin_"   + nonce, adminRole);
        regularUser = createUser("admin_api_user_"    + nonce, userRole);
        targetUser  = createUser("admin_api_target_"  + nonce, userRole);

        adminToken = jwtService.generateAccessToken(adminUser);
        userToken  = jwtService.generateAccessToken(regularUser);
    }

    @Test
    void shouldCreateStrategyAsAdmin() throws Exception {
        Map<String, Object> body = Map.of(
                "versionLabel", "v1",
                "creditScoreWeight", 0.3,
                "positiveRateWeight", 0.4,
                "reviewQualityWeight", 0.3,
                "minCreditScoreThreshold", 50,
                "minPositiveRateThreshold", 0.5
        );

        MvcResult result = mockMvc.perform(post("/api/admin/strategies")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionLabel").value("v1"))
                .andExpect(jsonPath("$.active").value(false))
                .andReturn();

        var node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(node.has("id")).isTrue();
        long id = node.get("id").asLong();
        assertThat(strategyRepository.existsById(id)).isTrue();
    }

    @Test
    void shouldListStrategiesAsAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/strategies")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldActivateStrategyAsAdmin() throws Exception {
        // Create a strategy first
        RankingStrategyVersion strategy = buildStrategy(adminUser.getId());
        strategy = strategyRepository.save(strategy);

        mockMvc.perform(put("/api/admin/strategies/" + strategy.getId() + "/activate")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // Verify strategyRepository.findByActiveTrue() returns the activated one
        var activeOpt = strategyRepository.findByActiveTrue();
        assertThat(activeOpt).isPresent();
        assertThat(activeOpt.get().getId()).isEqualTo(strategy.getId());
    }

    @Test
    void shouldGetActiveStrategy() throws Exception {
        // Ensure there is at least one active strategy
        RankingStrategyVersion strategy = buildStrategy(adminUser.getId());
        strategy.setActive(true);
        strategyRepository.save(strategy);

        mockMvc.perform(get("/api/admin/strategies/active")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldGetSearchAnalyticsAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/analytics/search")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSearches").isNumber())
                .andExpect(jsonPath("$.topTerms").isArray())
                .andExpect(jsonPath("$.uniqueKeywords").isNumber())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("totalSearches")).isTrue();
    }

    @Test
    void shouldGetAccessLogsAsAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/access-logs")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldRevealPiiAsAdmin() throws Exception {
        long countBefore = adminAccessLogRepository.count();

        MvcResult result = mockMvc.perform(
                post("/api/admin/users/" + targetUser.getId() + "/reveal")
                        .with(csrf())
                        .param("reason", "audit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").exists())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("email")).isTrue();

        // Verify adminAccessLog count incremented
        assertThat(adminAccessLogRepository.count()).isGreaterThan(countBefore);
    }

    /**
     * After admin POST /reveal, the adminAccessLog row must have the correct audit fields:
     * actionType="PII_REVEAL", fieldsRevealed contains "email", reason non-null,
     * adminUserId == admin.getId(), targetEntity="User", targetId == targetUser.getId().
     */
    @Test
    void shouldPersistRevealAuditLogFields() throws Exception {
        long countBefore = adminAccessLogRepository.count();

        mockMvc.perform(
                post("/api/admin/users/" + targetUser.getId() + "/reveal")
                        .with(csrf())
                        .param("reason", "compliance audit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Verify at least one new row was persisted
        assertThat(adminAccessLogRepository.count()).isGreaterThan(countBefore);

        // Find the just-created log entry for this target user
        var logs = adminAccessLogRepository.findAll();
        var logOpt = logs.stream()
                .filter(l -> "PII_REVEAL".equals(l.getActionType())
                        && targetUser.getId().equals(l.getTargetId()))
                .findFirst();

        assertThat(logOpt).as("A PII_REVEAL log entry for target user must exist").isPresent();
        var log = logOpt.get();

        assertThat(log.getActionType()).isEqualTo("PII_REVEAL");
        assertThat(log.getFieldsRevealed()).as("fieldsRevealed must be non-empty").isNotBlank();
        assertThat(log.getFieldsRevealed()).as("fieldsRevealed should include 'email'").contains("email");
        assertThat(log.getReason()).as("reason must be non-null").isNotNull();
        assertThat(log.getAdminUserId()).isEqualTo(adminUser.getId());
        assertThat(log.getTargetEntity()).isEqualTo("User");
        assertThat(log.getTargetId()).isEqualTo(targetUser.getId());
    }

    @Test
    void shouldRejectAllAdminEndpointsForRegularUser() throws Exception {
        // strategies list
        int status1 = mockMvc.perform(get("/api/admin/strategies")
                .header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getStatus();
        assertThat(status1).isEqualTo(403);

        // analytics
        int status2 = mockMvc.perform(get("/api/admin/analytics/search")
                .header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getStatus();
        assertThat(status2).isEqualTo(403);

        // reveal PII
        int status3 = mockMvc.perform(
                post("/api/admin/users/" + targetUser.getId() + "/reveal")
                        .with(csrf())
                        .param("reason", "test")
                        .header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getStatus();
        assertThat(status3).isEqualTo(403);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private RankingStrategyVersion buildStrategy(Long adminId) {
        RankingStrategyVersion s = new RankingStrategyVersion();
        s.setVersionLabel("test-v-" + System.nanoTime());
        s.setCreditScoreWeight(new BigDecimal("0.3"));
        s.setPositiveRateWeight(new BigDecimal("0.4"));
        s.setReviewQualityWeight(new BigDecimal("0.3"));
        s.setMinCreditScoreThreshold(new BigDecimal("50"));
        s.setMinPositiveRateThreshold(new BigDecimal("0.5"));
        s.setActive(false);
        s.setCreatedBy(adminId);
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }

    private Role findOrCreateRole(String name) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
    }

    private User createUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        user.setEmail(username + "@example.com");
        user.setEnabled(true);
        user.setLocked(false);
        user.setForcePasswordReset(false);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>(Set.of(role)));
        return userRepository.save(user);
    }
}
