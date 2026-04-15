package com.reclaim.portal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.auth.entity.AdminAccessLog;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.AdminAccessLogRepository;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private AdminAccessLogRepository adminAccessLogRepository;

    private User userA;
    private User userB;
    private User adminUser;
    private User reviewerUser;

    private String userAToken;
    private String userBToken;
    private String adminToken;
    private String reviewerToken;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role adminRole    = findOrCreateRole("ROLE_ADMIN");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");

        userA        = createUser("userA_" + nonce, "User Alpha", userRole);
        userB        = createUser("userB_" + nonce, "User Beta",  userRole);
        adminUser    = createUser("admin_ua_" + nonce, null,   adminRole);
        reviewerUser = createUser("reviewer_ua_" + nonce, null, reviewerRole);

        userAToken    = jwtService.generateAccessToken(userA);
        userBToken    = jwtService.generateAccessToken(userB);
        adminToken    = jwtService.generateAccessToken(adminUser);
        reviewerToken = jwtService.generateAccessToken(reviewerUser);
    }

    @Test
    void shouldReturnFullProfileForSelf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/" + userA.getId() + "/profile")
                .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        // Self view returns full unmasked email
        assertThat(body.has("email")).isTrue();
        assertThat(body.get("email").asText()).isEqualTo(userA.getEmail());
        assertThat(body.get("username").asText()).isEqualTo(userA.getUsername());
    }

    @Test
    void shouldReturnMaskedProfileForAdmin() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/" + userA.getId() + "/profile")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("maskedEmail")).isTrue();
        // Admin gets masked view — 'email' field should not exist in the masked DTO
        assertThat(body.has("email")).isFalse();
    }

    @Test
    void shouldReturnMaskedProfileForReviewer() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/" + userA.getId() + "/profile")
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("maskedEmail")).isTrue();
    }

    @Test
    void shouldDenyProfileAccessForOtherUser() throws Exception {
        // userB tries to view userA's profile
        mockMvc.perform(get("/api/users/" + userA.getId() + "/profile")
                .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden()); // BusinessRuleException "Access denied to user profile" → 403
    }

    @Test
    void shouldRevealPiiAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(
                post("/api/users/" + userA.getId() + "/reveal")
                        .with(csrf())
                        .param("reason", "test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("email")).isTrue();

        // Verify admin access log was written with actionType PII_REVEAL
        List<AdminAccessLog> logs = adminAccessLogRepository
                .findByAdminUserIdOrderByCreatedAtDesc(adminUser.getId());
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getActionType()).isEqualTo("PII_REVEAL");
    }

    @Test
    void shouldRejectRevealAsUser() throws Exception {
        mockMvc.perform(
                post("/api/users/" + userA.getId() + "/reveal")
                        .with(csrf())
                        .param("reason", "test")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // POST /api/users/{id}/reveal additional tests
    // =========================================================================

    @Test
    void shouldAuditLogPiiRevealWithActorAndReason() throws Exception {
        mockMvc.perform(
                post("/api/users/" + userA.getId() + "/reveal")
                        .with(csrf())
                        .param("reason", "audit-xyz-123")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        List<AdminAccessLog> logs = adminAccessLogRepository
                .findByAdminUserIdOrderByCreatedAtDesc(adminUser.getId());
        assertThat(logs).isNotEmpty();

        AdminAccessLog log = logs.get(0);
        assertThat(log.getActionType()).isEqualTo("PII_REVEAL");
        assertThat(log.getFieldsRevealed()).isNotBlank();
        assertThat(log.getReason()).contains("audit-xyz-123");
    }

    @Test
    void shouldRejectPiiRevealWithoutReason() throws Exception {
        // Missing required @RequestParam "reason" →
        // MissingServletRequestParameterException → GlobalExceptionHandler → 400
        mockMvc.perform(
                post("/api/users/" + userA.getId() + "/reveal")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAllowRepeatRevealAndLogBoth() throws Exception {
        mockMvc.perform(
                post("/api/users/" + userA.getId() + "/reveal")
                        .with(csrf())
                        .param("reason", "first-reveal")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(
                post("/api/users/" + userA.getId() + "/reveal")
                        .with(csrf())
                        .param("reason", "second-reveal")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        List<AdminAccessLog> logs = adminAccessLogRepository
                .findByAdminUserIdOrderByCreatedAtDesc(adminUser.getId());
        assertThat(logs.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldDenyRevealForNonAdmin() throws Exception {
        // reviewer → 403
        mockMvc.perform(
                post("/api/users/" + userA.getId() + "/reveal")
                        .with(csrf())
                        .param("reason", "test")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isForbidden());

        // regular user → 403
        mockMvc.perform(
                post("/api/users/" + userA.getId() + "/reveal")
                        .with(csrf())
                        .param("reason", "test")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // GET /api/users/{id}/profile additional tests
    // =========================================================================

    @Test
    void shouldReturnMaskedEmailFormatForAdmin() throws Exception {
        // Admin receives masked profile; maskedEmail must contain at least one '*'
        mockMvc.perform(get("/api/users/" + userA.getId() + "/profile")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedEmail")
                        .value(org.hamcrest.Matchers.matchesPattern(".*\\*+.*")));
    }

    @Test
    void shouldReturnMaskedPhoneFormatForReviewer() throws Exception {
        // Reviewer receives masked profile; maskedPhone field should exist and contain '*'
        // (maskedPhone is "***" when phone is null/empty which still contains '*')
        mockMvc.perform(get("/api/users/" + userA.getId() + "/profile")
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedPhone")
                        .value(org.hamcrest.Matchers.matchesPattern(".*\\*+.*")));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Role findOrCreateRole(String name) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
    }

    private User createUser(String username, String fullName, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        user.setEmail(username + "@example.com");
        if (fullName != null) user.setFullName(fullName);
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
