package com.reclaim.portal.e2e;

import com.reclaim.portal.auth.entity.AdminAccessLog;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.AdminAccessLogRepository;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-HTTP coverage for user profile endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpUserProfileTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private AdminAccessLogRepository adminAccessLogRepository;

    private User userA;
    private User userB;
    private User reviewer;
    private User admin;

    private String userAToken;
    private String userBToken;
    private String reviewerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role adminRole    = findOrCreateRole("ROLE_ADMIN");

        userA    = createUser("prof_userA_"    + nonce, userRole);
        userB    = createUser("prof_userB_"    + nonce, userRole);
        reviewer = createUser("prof_reviewer_" + nonce, reviewerRole);
        admin    = createUser("prof_admin_"    + nonce, adminRole);

        userAToken    = jwtService.generateAccessToken(userA);
        userBToken    = jwtService.generateAccessToken(userB);
        reviewerToken = jwtService.generateAccessToken(reviewer);
        adminToken    = jwtService.generateAccessToken(admin);
    }

    // =========================================================================
    // 1. Full profile for self
    // =========================================================================

    @Test
    void shouldReturnFullProfileForSelfOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userAToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/users/" + userA.getId() + "/profile",
                HttpMethod.GET, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        // Full profile should contain real email
        assertThat(resp.getBody().get("email")).isEqualTo(userA.getEmail());
    }

    // =========================================================================
    // 2. Masked profile for reviewer
    // =========================================================================

    @Test
    void shouldReturnMaskedForReviewerOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(reviewerToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/users/" + userA.getId() + "/profile",
                HttpMethod.GET, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();

        // Masked DTO has maskedEmail, not email
        String maskedEmail = (String) resp.getBody().get("maskedEmail");
        assertThat(maskedEmail).isNotNull().contains("***");
        // Should not expose the real email field
        assertThat(resp.getBody()).doesNotContainKey("email");
    }

    // =========================================================================
    // 3. Masked profile for admin
    // =========================================================================

    @Test
    void shouldReturnMaskedForAdminOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(adminToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/users/" + userA.getId() + "/profile",
                HttpMethod.GET, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        String maskedEmail = (String) resp.getBody().get("maskedEmail");
        assertThat(maskedEmail).isNotNull().contains("***");
    }

    // =========================================================================
    // 4. Other user cannot access userA's profile → 403
    // (BusinessRuleException with message starting "Access denied" maps to FORBIDDEN)
    // =========================================================================

    @Test
    void shouldDenyOtherUserProfileAccessOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userBToken));
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/users/" + userA.getId() + "/profile",
                HttpMethod.GET, req, String.class);

        // GlobalExceptionHandler maps BusinessRuleException("Access denied...") → 403
        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // =========================================================================
    // 5. Admin reveal PII → returns full profile + logs audit row
    // =========================================================================

    @Test
    void shouldRevealPiiAsAdminOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(csrfAuthHeaders(adminToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/admin/users/" + userA.getId() + "/reveal?reason=audit-real-http",
                HttpMethod.POST, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("email")).isEqualTo(userA.getEmail());

        // Verify audit log row was written
        List<AdminAccessLog> logs = adminAccessLogRepository
                .findByAdminUserIdOrderByCreatedAtDesc(admin.getId());
        assertThat(logs).isNotEmpty();
        boolean hasPiiReveal = logs.stream()
                .anyMatch(l -> "PII_REVEAL".equals(l.getActionType())
                        && userA.getId().equals(l.getTargetId()));
        assertThat(hasPiiReveal).as("PII_REVEAL audit log should exist").isTrue();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) {
            h.setBearerAuth(token);
        }
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        return h;
    }

    private HttpHeaders csrfAuthHeaders(String token) {
        ResponseEntity<String> probe = restTemplate.getForEntity(base() + "/login", String.class);
        String xsrf = null;
        List<String> cookies = probe.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String c : cookies) {
                if (c.startsWith("XSRF-TOKEN=")) {
                    xsrf = c.substring("XSRF-TOKEN=".length()).split(";", 2)[0];
                    break;
                }
            }
        }
        HttpHeaders h = authHeaders(token);
        if (xsrf != null) {
            h.add("Cookie", "XSRF-TOKEN=" + xsrf);
            h.add("X-XSRF-TOKEN", xsrf);
        }
        return h;
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
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        u.setEmail(username + "@example.com");
        u.setEnabled(true);
        u.setLocked(false);
        u.setForcePasswordReset(false);
        u.setFailedAttempts(0);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        u.setRoles(new HashSet<>(Set.of(role)));
        return userRepository.save(u);
    }
}
