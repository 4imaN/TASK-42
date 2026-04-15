package com.reclaim.portal.e2e;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.search.service.SearchService;
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
 * Real-HTTP coverage for search/trending/autocomplete endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpSearchTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private SearchService searchService;

    private String userToken;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();
        Role userRole = findOrCreateRole("ROLE_USER");
        User user = createUser("srch_user_" + nonce, userRole);
        userToken = jwtService.generateAccessToken(user);
    }

    // =========================================================================
    // 1. Trending searches
    // =========================================================================

    @Test
    void shouldReturnTrendingSearchesOverRealHttp() {
        // Seed widget 3 times, gadget once
        searchService.updateTrends("widget");
        searchService.updateTrends("widget");
        searchService.updateTrends("widget");
        searchService.updateTrends("gadget");

        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                base() + "/api/search/trending",
                HttpMethod.GET, req,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull().isNotEmpty();

        boolean foundWidget = resp.getBody().stream()
                .anyMatch(entry ->
                    "widget".equals(entry.get("searchTerm"))
                    && entry.get("searchCount") instanceof Number
                    && ((Number) entry.get("searchCount")).intValue() >= 3);
        assertThat(foundWidget).as("trending should contain 'widget' with searchCount >= 3").isTrue();
    }

    // =========================================================================
    // 2. Autocomplete
    // =========================================================================

    @Test
    void shouldReturnAutocompleteSuggestionsOverRealHttp() {
        long nonce = System.nanoTime();
        String termA = "widget-a-" + nonce;
        String termB = "widget-b-" + nonce;
        String unrelated = "unrelated-" + nonce;

        searchService.updateTrends(termA);
        searchService.updateTrends(termB);
        searchService.updateTrends(unrelated);

        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<List<String>> resp = restTemplate.exchange(
                base() + "/api/search/autocomplete?q=widget-a-" + nonce,
                HttpMethod.GET, req,
                new ParameterizedTypeReference<List<String>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).contains(termA);
        assertThat(resp.getBody()).doesNotContain(unrelated);
    }

    // =========================================================================
    // 3. Empty autocomplete for no match
    // =========================================================================

    @Test
    void shouldReturnEmptyAutocompleteForNoMatchOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<List<String>> resp = restTemplate.exchange(
                base() + "/api/search/autocomplete?q=qqqzzzNoMatch999",
                HttpMethod.GET, req,
                new ParameterizedTypeReference<List<String>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull().isEmpty();
    }

    // =========================================================================
    // 4. Catalog search logs term → appears in trending
    // =========================================================================

    @Test
    void shouldLogSearchViaCatalogAndAppearInTrendingOverRealHttp() {
        long nonce = System.nanoTime();
        String novelTerm = "novelBrowserTerm_" + nonce;

        // Hit catalog search — CatalogService calls searchService.updateTrends internally
        HttpEntity<Void> catalogReq = new HttpEntity<>(authHeaders(userToken));
        restTemplate.exchange(
                base() + "/api/catalog/search?keyword=" + novelTerm,
                HttpMethod.GET, catalogReq, Map.class);

        // Now check trending
        HttpEntity<Void> trendReq = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<List<Map<String, Object>>> trendResp = restTemplate.exchange(
                base() + "/api/search/trending",
                HttpMethod.GET, trendReq,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(trendResp.getStatusCode().value()).isEqualTo(200);
        boolean found = trendResp.getBody().stream()
                .anyMatch(e -> novelTerm.equals(e.get("searchTerm")));
        assertThat(found).as("catalog search should log term that appears in trending").isTrue();
    }

    // =========================================================================
    // 5. Trending requires auth
    // =========================================================================

    @Test
    void shouldRequireAuthForTrendingOverRealHttp() {
        // No Authorization header
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<Void> req = new HttpEntity<>(h);

        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/search/trending",
                HttpMethod.GET, req, String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
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
