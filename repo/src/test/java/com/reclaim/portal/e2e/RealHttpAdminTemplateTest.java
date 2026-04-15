package com.reclaim.portal.e2e;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-HTTP tests for admin contract template management endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpAdminTemplateTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private String adminToken;
    private String regularUserToken;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role adminRole = findOrCreateRole("ROLE_ADMIN");
        Role userRole  = findOrCreateRole("ROLE_USER");

        User admin       = createUser("at_admin_"   + nonce, adminRole);
        User regularUser = createUser("at_regular_" + nonce, userRole);

        adminToken       = jwtService.generateAccessToken(admin);
        regularUserToken = jwtService.generateAccessToken(regularUser);
    }

    // =========================================================================
    // 1. Create contract template
    // =========================================================================

    @Test
    void shouldCreateContractTemplateOverRealHttp() {
        Map<String, String> body = Map.of("name", "Demo_" + System.nanoTime(), "description", "d");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(adminToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/contracts/templates", HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("id")).isNotNull();
        assertThat((String) resp.getBody().get("name")).startsWith("Demo_");
        assertThat(resp.getBody().get("active")).isEqualTo(true);
    }

    // =========================================================================
    // 2. Create template version
    // =========================================================================

    @Test
    void shouldCreateTemplateVersionOverRealHttp() {
        Long templateId = createTemplate();

        Map<String, String> body = Map.of("content", "Hello {{x}}", "changeNotes", "v1");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(adminToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/contracts/templates/" + templateId + "/versions",
                HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        Number versionNumber = (Number) resp.getBody().get("versionNumber");
        assertThat(versionNumber).isNotNull();
        assertThat(versionNumber.intValue()).isGreaterThanOrEqualTo(1);
    }

    // =========================================================================
    // 3. Add clause field
    // =========================================================================

    @Test
    void shouldAddClauseFieldOverRealHttp() {
        Long templateId = createTemplate();
        Long versionId  = createVersion(templateId);

        Map<String, Object> body = Map.of(
                "fieldName",    "partyName",
                "fieldType",    "TEXT",
                "fieldLabel",   "Party",
                "required",     true,
                "defaultValue", "Unknown",
                "displayOrder", 1);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, csrfAuthHeaders(adminToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/contracts/templates/versions/" + versionId + "/fields",
                HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("fieldName")).isEqualTo("partyName");
        assertThat(resp.getBody().get("required")).isEqualTo(true);
    }

    // =========================================================================
    // 4. List active templates
    // =========================================================================

    @Test
    void shouldListActiveTemplatesOverRealHttp() {
        // Ensure at least one template exists
        createTemplate();

        HttpEntity<Void> req = new HttpEntity<>(authHeaders(adminToken));
        ResponseEntity<List> resp = restTemplate.exchange(
                base() + "/api/contracts/templates", HttpMethod.GET, req, List.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).isNotEmpty();
    }

    // =========================================================================
    // 5. Reject template creation for regular user (403)
    // =========================================================================

    @Test
    void shouldRejectTemplateCreationForRegularUserOverRealHttp() {
        Map<String, String> body = Map.of("name", "Unauthorized_" + System.nanoTime(), "description", "d");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(regularUserToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/contracts/templates", HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isBetween(400, 499);
        assertThat(resp.getStatusCode().value()).isNotEqualTo(200);
    }

    // =========================================================================
    // 6. Get clause fields for version
    // =========================================================================

    @Test
    void shouldGetClauseFieldsForVersionOverRealHttp() {
        Long templateId = createTemplate();
        Long versionId  = createVersion(templateId);

        // Add a field first
        Map<String, Object> fieldBody = Map.of(
                "fieldName",    "companyName",
                "fieldType",    "TEXT",
                "fieldLabel",   "Company",
                "required",     false,
                "defaultValue", "Acme",
                "displayOrder", 1);
        HttpEntity<Map<String, Object>> addReq = new HttpEntity<>(fieldBody, csrfAuthHeaders(adminToken));
        restTemplate.exchange(
                base() + "/api/contracts/templates/versions/" + versionId + "/fields",
                HttpMethod.POST, addReq, Map.class);

        // GET fields
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(adminToken));
        ResponseEntity<List> resp = restTemplate.exchange(
                base() + "/api/contracts/templates/versions/" + versionId + "/fields",
                HttpMethod.GET, req, List.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).isNotEmpty();

        boolean found = ((List<?>) resp.getBody()).stream()
                .filter(f -> f instanceof Map)
                .map(f -> (Map<?, ?>) f)
                .anyMatch(f -> "companyName".equals(f.get("fieldName")));
        assertThat(found).as("fields list should contain the added field").isTrue();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Long createTemplate() {
        Map<String, String> body = Map.of("name", "T_" + System.nanoTime(), "description", "auto");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(adminToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/contracts/templates", HttpMethod.POST, req, Map.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    private Long createVersion(Long templateId) {
        Map<String, String> body = Map.of("content", "Hello {{x}}", "changeNotes", "v1");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(adminToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/contracts/templates/" + templateId + "/versions",
                HttpMethod.POST, req, Map.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
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
