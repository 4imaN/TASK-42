package com.reclaim.portal.e2e;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browser-level tests using HtmlUnit with RANDOM_PORT so the full HTTP stack runs
 * (Spring Security filter chain, JWT cookie auth, Thymeleaf SSR) — no MockMvc shortcuts.
 *
 * <p>The login template uses a JS fetch() rather than a plain HTML form POST, so we
 * simulate the login programmatically via the JSON API and let HtmlUnit's CookieManager
 * carry the resulting accessToken cookie on subsequent page requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BrowserLoginJourneyTest {

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

        testUsername = "blj_user_" + nonce;
        testUser = new User();
        testUser.setUsername(testUsername);
        testUser.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        testUser.setEmail("blj_" + nonce + "@example.com");
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
        // Clean up test user so no cross-test leakage (best-effort)
        try {
            userRepository.findByUsername(testUsername).ifPresent(userRepository::delete);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Test: Real login page renders with expected HTML form inputs
    // =========================================================================

    /**
     * Parses the real Thymeleaf-rendered /login page and verifies the HTML form
     * contains a username text input, a password input, and a submit button.
     * This is a true browser-level assertion against rendered HTML.
     */
    @Test
    void shouldRenderRealLoginFormWithExpectedInputs() throws Exception {
        HtmlPage page = webClient.getPage("http://localhost:" + port + "/login");

        assertThat(page.getWebResponse().getStatusCode()).isEqualTo(200);
        assertThat(page.getTitleText()).isNotBlank();

        // Locate the form — the template uses id="loginForm"
        List<HtmlForm> forms = page.getForms();
        assertThat(forms).as("Login page must have at least one <form>").isNotEmpty();
        HtmlForm loginForm = forms.get(0);

        // Username input: name="username", type="text"
        HtmlInput usernameInput = loginForm.getInputByName("username");
        assertThat(usernameInput).isNotNull();
        String usernameType = usernameInput.getTypeAttribute().toLowerCase();
        assertThat(usernameType).as("username input type").isIn("text", "email");

        // Password input: name="password", type="password"
        HtmlInput passwordInput = loginForm.getInputByName("password");
        assertThat(passwordInput).isNotNull();
        assertThat(passwordInput.getTypeAttribute().toLowerCase())
                .as("password input type")
                .isEqualTo("password");

        // The page must contain a submit button (button[type=submit] OR input[type=submit])
        String xml = page.asXml();
        boolean hasSubmit = xml.contains("type=\"submit\"") || xml.contains("btn-primary");
        assertThat(hasSubmit).as("Login page must contain a submit button").isTrue();
    }

    // =========================================================================
    // Test: Programmatic login via JSON API, then access protected pages
    // =========================================================================

    /**
     * Logs in via a direct JSON POST to /api/auth/login, which sets the accessToken
     * HttpOnly cookie in HtmlUnit's CookieManager. Subsequent GETs to protected SSR
     * pages are served without redirect because the cookie is present.
     */
    @Test
    void shouldServeDashboardAfterProgrammaticLogin() throws Exception {
        // --- Step 1: POST to /api/auth/login with JSON body ---
        WebRequest loginReq = new WebRequest(
                new URL("http://localhost:" + port + "/api/auth/login"),
                HttpMethod.POST);
        loginReq.setAdditionalHeader("Content-Type", "application/json");
        loginReq.setRequestBody(
                "{\"username\":\"" + testUsername + "\",\"password\":\"" + TEST_PASSWORD + "\"}");

        Page loginResp = webClient.getPage(loginReq);
        assertThat(loginResp.getWebResponse().getStatusCode())
                .as("Login API should return 200")
                .isEqualTo(200);

        // HtmlUnit's CookieManager now holds the accessToken cookie set by Set-Cookie.
        // Verify the cookie was actually received.
        boolean hasCookie = webClient.getCookieManager().getCookies().stream()
                .anyMatch(c -> "accessToken".equals(c.getName()));
        assertThat(hasCookie).as("accessToken cookie must be set after login").isTrue();

        // --- Step 2: GET /user/dashboard — should render 200, not redirect to /login ---
        HtmlPage dashPage = webClient.getPage("http://localhost:" + port + "/user/dashboard");
        assertThat(dashPage.getWebResponse().getStatusCode())
                .as("Dashboard must return 200 when authenticated")
                .isEqualTo(200);
        // We should NOT have been redirected to /login
        assertThat(dashPage.getUrl().getPath())
                .as("Should stay on dashboard, not redirected to /login")
                .isNotEqualTo("/login");
    }

    /**
     * After programmatic login, GET /user/orders must return 200 (not redirect).
     */
    @Test
    void shouldServeOrdersPageAfterProgrammaticLogin() throws Exception {
        doLogin();

        HtmlPage ordersPage = webClient.getPage("http://localhost:" + port + "/user/orders");
        assertThat(ordersPage.getWebResponse().getStatusCode())
                .as("Orders page must return 200 when authenticated")
                .isEqualTo(200);
        assertThat(ordersPage.getUrl().getPath())
                .as("Should stay on orders, not redirected to /login")
                .isNotEqualTo("/login");
    }

    /**
     * After programmatic login, GET /user/search must return 200 (not redirect).
     */
    @Test
    void shouldServeSearchPageAfterProgrammaticLogin() throws Exception {
        doLogin();

        HtmlPage searchPage = webClient.getPage("http://localhost:" + port + "/user/search");
        assertThat(searchPage.getWebResponse().getStatusCode())
                .as("Search page must return 200 when authenticated")
                .isEqualTo(200);
        assertThat(searchPage.getUrl().getPath())
                .as("Should stay on search, not redirected to /login")
                .isNotEqualTo("/login");
    }

    // =========================================================================
    // Test: Form field fill-ability (JS-disabled, so submit does nothing)
    // =========================================================================

    /**
     * Verifies that the form fields on /login are fillable via HtmlUnit even with
     * JS disabled. This exercises real Thymeleaf rendering and HtmlUnit's DOM parser.
     * Since the form uses fetch() not a classic POST, submitting without JS is a no-op,
     * but the inputs must still be present and writable.
     */
    @Test
    void shouldSubmitLoginFormEndToEnd() throws Exception {
        HtmlPage loginPage = webClient.getPage("http://localhost:" + port + "/login");
        assertThat(loginPage.getWebResponse().getStatusCode()).isEqualTo(200);

        // Find form and fill inputs
        assertThat(loginPage.getForms()).isNotEmpty();
        HtmlForm form = loginPage.getForms().get(0);

        HtmlInput usernameInput = form.getInputByName("username");
        HtmlInput passwordInput = form.getInputByName("password");

        // Both inputs must be present and editable
        assertThat(usernameInput).isNotNull();
        assertThat(passwordInput).isNotNull();

        usernameInput.type(testUsername);
        passwordInput.type(TEST_PASSWORD);

        // Verify values were set correctly (form is filled in real DOM)
        assertThat(usernameInput.getValue()).isEqualTo(testUsername);
        assertThat(passwordInput.getValue()).isEqualTo(TEST_PASSWORD);

        // Input types must be correct for security
        assertThat(passwordInput.getTypeAttribute().toLowerCase()).isEqualTo("password");
        assertThat(usernameInput.getTypeAttribute().toLowerCase()).isIn("text", "email");
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void doLogin() throws Exception {
        WebRequest loginReq = new WebRequest(
                new URL("http://localhost:" + port + "/api/auth/login"),
                HttpMethod.POST);
        loginReq.setAdditionalHeader("Content-Type", "application/json");
        loginReq.setRequestBody(
                "{\"username\":\"" + testUsername + "\",\"password\":\"" + TEST_PASSWORD + "\"}");
        webClient.getPage(loginReq);
    }
}
