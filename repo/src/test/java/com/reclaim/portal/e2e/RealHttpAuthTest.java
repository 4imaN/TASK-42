package com.reclaim.portal.e2e;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
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
 * Real-HTTP coverage for auth endpoints: login, refresh, logout.
 * These endpoints are CSRF-exempt (SameSite=Strict + Origin validation for refresh/logout).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpAuthTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String testUsername;
    private static final String TEST_PASSWORD = "TestPassword1!";

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();
        testUsername = "auth_user_" + nonce;

        Role userRole = findOrCreateRole("ROLE_USER");
        createUser(testUsername, userRole);
    }

    // =========================================================================
    // 1. Login with valid credentials
    // =========================================================================

    @Test
    void shouldLoginWithValidCredentialsOverRealHttp() {
        Map<String, String> body = Map.of("username", testUsername, "password", TEST_PASSWORD);
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, jsonHeaders());

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/auth/login",
                HttpMethod.POST, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("accessToken")).isNotNull();
        assertThat((String) resp.getBody().get("accessToken")).isNotBlank();

        // refreshToken cookie should be present in Set-Cookie
        List<String> setCookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        boolean hasRefreshCookie = setCookies.stream()
                .anyMatch(c -> c.startsWith("refreshToken="));
        assertThat(hasRefreshCookie).as("response should set HttpOnly refreshToken cookie").isTrue();
    }

    // =========================================================================
    // 2. Reject login with wrong password
    // =========================================================================

    @Test
    void shouldRejectLoginWithWrongPasswordOverRealHttp() {
        Map<String, String> body = Map.of("username", testUsername, "password", "WrongPassword!");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, jsonHeaders());

        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/auth/login",
                HttpMethod.POST, req, String.class);

        // BusinessRuleException → 409 Conflict
        assertThat(resp.getStatusCode().value()).isEqualTo(409);
    }

    // =========================================================================
    // 3. Reject login with non-existent user
    // =========================================================================

    @Test
    void shouldRejectLoginWithNonexistentUserOverRealHttp() {
        Map<String, String> body = Map.of("username", "no_such_user_xyz_" + System.nanoTime(),
                "password", TEST_PASSWORD);
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, jsonHeaders());

        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/auth/login",
                HttpMethod.POST, req, String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(409);
    }

    // =========================================================================
    // 4. Refresh access token
    // =========================================================================

    @Test
    void shouldRefreshAccessTokenOverRealHttp() {
        // Step 1: login to get tokens
        Map<String, String> loginBody = Map.of("username", testUsername, "password", TEST_PASSWORD);
        HttpEntity<Map<String, String>> loginReq = new HttpEntity<>(loginBody, jsonHeaders());
        ResponseEntity<Map<String, Object>> loginResp = restTemplate.exchange(
                base() + "/api/auth/login",
                HttpMethod.POST, loginReq,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(loginResp.getStatusCode().value()).isEqualTo(200);
        String firstAccessToken = (String) loginResp.getBody().get("accessToken");
        assertThat(firstAccessToken).isNotBlank();

        // Extract refreshToken cookie value
        String refreshCookieValue = extractCookieValue(loginResp.getHeaders(), "refreshToken");
        assertThat(refreshCookieValue).as("refreshToken cookie must be present after login").isNotBlank();

        // Step 2: call refresh with the cookie
        // The refresh endpoint uses SameSite cookie-auth (no CSRF needed).
        // Origin validation: null origin is allowed per validateOrigin logic.
        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.setContentType(MediaType.APPLICATION_JSON);
        refreshHeaders.add("Cookie", "refreshToken=" + refreshCookieValue);

        HttpEntity<Void> refreshReq = new HttpEntity<>(refreshHeaders);
        ResponseEntity<Map<String, Object>> refreshResp = restTemplate.exchange(
                base() + "/api/auth/refresh",
                HttpMethod.POST, refreshReq,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(refreshResp.getStatusCode().value()).isEqualTo(200);
        String newAccessToken = (String) refreshResp.getBody().get("accessToken");
        assertThat(newAccessToken).isNotBlank();
        assertThat(newAccessToken).as("new access token should differ from the first").isNotEqualTo(firstAccessToken);
    }

    // =========================================================================
    // 5. Logout clears the refresh cookie
    // =========================================================================

    @Test
    void shouldLogoutOverRealHttp() {
        // Step 1: login
        Map<String, String> loginBody = Map.of("username", testUsername, "password", TEST_PASSWORD);
        HttpEntity<Map<String, String>> loginReq = new HttpEntity<>(loginBody, jsonHeaders());
        ResponseEntity<Map<String, Object>> loginResp = restTemplate.exchange(
                base() + "/api/auth/login",
                HttpMethod.POST, loginReq,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(loginResp.getStatusCode().value()).isEqualTo(200);
        String refreshCookieValue = extractCookieValue(loginResp.getHeaders(), "refreshToken");
        assertThat(refreshCookieValue).isNotBlank();

        // Step 2: logout
        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.setContentType(MediaType.APPLICATION_JSON);
        logoutHeaders.add("Cookie", "refreshToken=" + refreshCookieValue);

        HttpEntity<Void> logoutReq = new HttpEntity<>(logoutHeaders);
        ResponseEntity<Void> logoutResp = restTemplate.exchange(
                base() + "/api/auth/logout",
                HttpMethod.POST, logoutReq, Void.class);

        assertThat(logoutResp.getStatusCode().value()).isEqualTo(200);

        // Response should clear the cookie (max-age=0)
        List<String> setCookies = logoutResp.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        boolean cookieCleared = setCookies.stream()
                .anyMatch(c -> c.startsWith("refreshToken=") && c.contains("Max-Age=0"));
        assertThat(cookieCleared).as("logout response should clear the refreshToken cookie").isTrue();
    }

    // =========================================================================
    // 6. Refresh without cookie → 400
    // =========================================================================

    @Test
    void shouldRejectRefreshWithoutCookieOverRealHttp() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> req = new HttpEntity<>(h);

        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/auth/refresh",
                HttpMethod.POST, req, String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        return h;
    }

    /**
     * Extracts a named cookie's value from Set-Cookie response headers.
     */
    private String extractCookieValue(HttpHeaders headers, String cookieName) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) return null;
        for (String c : setCookies) {
            if (c.startsWith(cookieName + "=")) {
                return c.substring((cookieName + "=").length()).split(";", 2)[0];
            }
        }
        return null;
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
        u.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
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
