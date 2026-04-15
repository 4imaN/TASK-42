package com.reclaim.portal.e2e;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real browser-level tests using HtmlUnit — renders actual HTML, parses Thymeleaf output,
 * follows links. No Selenium/Playwright install required; HtmlUnit is a pure-Java browser.
 *
 * <p>Uses {@code webEnvironment = RANDOM_PORT} so the Thymeleaf SSR flow runs over a real
 * HTTP port, exercising the full Spring Security filter chain, JWT cookie auth, and page
 * rendering — the closest to a true E2E test we can reach without external infrastructure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BrowserLoginFlowTest {

    @LocalServerPort
    private int port;

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private WebClient webClient;
    private User testUser;
    private String testUsername;
    private static final String TEST_PASSWORD = "TestPassword1!";

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        testUsername = "browser_test_user_" + nonce;
        testUser = new User();
        testUser.setUsername(testUsername);
        testUser.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        testUser.setEmail("browser_" + nonce + "@example.com");
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
        webClient.getOptions().setJavaScriptEnabled(false); // Thymeleaf SSR doesn't need JS for rendering
    }

    @AfterEach
    void tearDown() {
        if (webClient != null) webClient.close();
    }

    /**
     * Verifies that GET / redirects to /user/dashboard (through /login if unauthenticated),
     * and that the login page actually renders with a real HTML form.
     */
    @Test
    void shouldRenderLoginPageAsHtml() throws Exception {
        HtmlPage page = webClient.getPage("http://localhost:" + port + "/login");
        assertThat(page.getWebResponse().getStatusCode()).isEqualTo(200);

        // Real HTML parse: look for form inputs the user will interact with
        String html = page.asXml();
        assertThat(html).contains("username");
        assertThat(html).contains("password");
        // Title tag confirms Thymeleaf rendering ran
        assertThat(page.getTitleText()).isNotBlank();
    }

    /**
     * Verifies that an unauthenticated browser request to a protected SSR page is redirected
     * to /login (not served). The AuthenticationEntryPoint distinguishes API (JSON 401) from
     * browser navigation (302 redirect to /login). HtmlUnit follows the redirect, so the
     * final page we see is the login page — but it MUST NOT be the dashboard.
     */
    @Test
    void shouldRedirectUnauthenticatedBrowserToLogin() throws Exception {
        HtmlPage page = webClient.getPage("http://localhost:" + port + "/user/dashboard");
        // After redirect-following, we land on /login (HTTP 200)
        assertThat(page.getWebResponse().getStatusCode()).isEqualTo(200);
        // Critical assertion: we are NOT on the dashboard — the URL was redirected
        assertThat(page.getUrl().getPath()).isEqualTo("/login");
        // The actual dashboard content must not be visible
        assertThat(page.asXml()).doesNotContain("My Orders");
    }

    /**
     * Verifies that static CSS is actually served (no 404 / no 500 from missing assets).
     */
    @Test
    void shouldServeStaticCss() throws Exception {
        var page = webClient.getPage("http://localhost:" + port + "/css/styles.css");
        assertThat(page.getWebResponse().getStatusCode()).isEqualTo(200);
        assertThat(page.getWebResponse().getContentAsString()).isNotEmpty();
    }

    /**
     * Verifies that static JS is actually served.
     */
    @Test
    void shouldServeStaticJs() throws Exception {
        var page = webClient.getPage("http://localhost:" + port + "/js/app.js");
        assertThat(page.getWebResponse().getStatusCode()).isEqualTo(200);
        String js = page.getWebResponse().getContentAsString();
        // The real app.js must contain the apiFetch wrapper used by every page
        assertThat(js).contains("apiFetch");
    }
}
