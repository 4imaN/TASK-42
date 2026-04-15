package com.reclaim.portal.e2e;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browser-level login failure tests using HtmlUnit with RANDOM_PORT.
 *
 * <p>Tests verify that:
 * - Invalid credentials return 409 (BusinessRuleException "Invalid username or password")
 *   and do NOT set an accessToken cookie.
 * - Non-existent usernames are rejected (409 or 404) without a cookie.
 * - After a failed login, the protected dashboard redirects to /login.
 * - A valid login sets the accessToken cookie.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BrowserLoginFailurePathsTest {

    @LocalServerPort
    private int port;

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private WebClient webClient;
    private User testUser;
    private String testUsername;
    private static final String CORRECT_PASSWORD = "TestPassword1!";
    private static final String WRONG_PASSWORD   = "WrongPassword9!";

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        testUsername = "blfp_user_" + nonce;
        testUser = new User();
        testUser.setUsername(testUsername);
        testUser.setPasswordHash(passwordEncoder.encode(CORRECT_PASSWORD));
        testUser.setEmail("blfp_" + nonce + "@example.com");
        testUser.setEnabled(true);
        testUser.setLocked(false);
        testUser.setForcePasswordReset(false);
        testUser.setFailedAttempts(0);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
        testUser.setRoles(new HashSet<>(Set.of(userRole)));
        testUser = userRepository.save(testUser);

        webClient = new WebClient();
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setJavaScriptEnabled(false);
        webClient.getOptions().setRedirectEnabled(true);
    }

    @AfterEach
    void tearDown() {
        if (webClient != null) {
            webClient.close();
        }
        try {
            userRepository.findByUsername(testUsername).ifPresent(userRepository::delete);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Failure scenarios
    // =========================================================================

    /**
     * POST /api/auth/login with valid username but wrong password.
     * The service throws BusinessRuleException("Invalid username or password"),
     * which GlobalExceptionHandler maps to 409 Conflict (not starting with "Access denied").
     * No accessToken cookie must be set.
     */
    @Test
    void shouldRejectLoginWithInvalidCredentials() throws Exception {
        Page response = doApiLogin(testUsername, WRONG_PASSWORD);

        // BusinessRuleException "Invalid username or password" → 409
        assertThat(response.getWebResponse().getStatusCode())
                .as("Wrong password should return 409")
                .isEqualTo(409);

        // No accessToken cookie must be set
        boolean hasCookie = webClient.getCookieManager().getCookies().stream()
                .anyMatch(c -> "accessToken".equals(c.getName()));
        assertThat(hasCookie).as("accessToken cookie must NOT be set after failed login").isFalse();
    }

    /**
     * POST /api/auth/login with a username that doesn't exist in the DB.
     * Expect 409 or 404 (the exact code depends on the AuthService implementation),
     * and no accessToken cookie is set.
     */
    @Test
    void shouldRejectLoginWithNonexistentUser() throws Exception {
        Page response = doApiLogin("no_such_user_xyzzy_" + System.nanoTime(), WRONG_PASSWORD);

        int status = response.getWebResponse().getStatusCode();
        // EntityNotFoundException → 404, or BusinessRuleException → 409
        assertThat(status)
                .as("Nonexistent user login should return 404 or 409")
                .isIn(404, 409);

        boolean hasCookie = webClient.getCookieManager().getCookies().stream()
                .anyMatch(c -> "accessToken".equals(c.getName()));
        assertThat(hasCookie).as("accessToken cookie must NOT be set for nonexistent user").isFalse();
    }

    /**
     * After a failed login attempt, GET /user/dashboard should redirect to /login
     * because no valid accessToken cookie was set.
     */
    @Test
    void shouldNotAllowAccessToDashboardAfterFailedLogin() throws Exception {
        // Attempt a failed login (wrong password)
        doApiLogin(testUsername, WRONG_PASSWORD);

        // Attempt to access the protected dashboard page
        Page dashPage = webClient.getPage("http://localhost:" + port + "/user/dashboard");

        // Should end up at /login (because no valid cookie was set)
        String finalPath = dashPage.getUrl().getPath();
        assertThat(finalPath)
                .as("After failed login, dashboard access should redirect to /login")
                .isEqualTo("/login");
    }

    // =========================================================================
    // Success scenario
    // =========================================================================

    /**
     * Valid credentials → after login, the accessToken cookie is set in HtmlUnit's CookieManager
     * with a non-empty value. Mirrors pattern from BrowserLoginJourneyTest.
     */
    @Test
    void shouldSetAccessTokenCookieAfterValidLogin() throws Exception {
        Page response = doApiLogin(testUsername, CORRECT_PASSWORD);

        assertThat(response.getWebResponse().getStatusCode())
                .as("Valid login should return 200")
                .isEqualTo(200);

        org.htmlunit.util.Cookie cookie = webClient.getCookieManager().getCookie("accessToken");
        assertThat(cookie)
                .as("accessToken cookie must be set after valid login")
                .isNotNull();
        assertThat(cookie.getValue())
                .as("accessToken cookie value must be non-empty")
                .isNotBlank();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Page doApiLogin(String username, String password) throws Exception {
        WebRequest loginReq = new WebRequest(
                new URL("http://localhost:" + port + "/api/auth/login"),
                HttpMethod.POST);
        loginReq.setAdditionalHeader("Content-Type", "application/json");
        loginReq.setRequestBody(
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        return webClient.getPage(loginReq);
    }
}
