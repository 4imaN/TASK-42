package com.reclaim.portal.e2e;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-HTTP coverage for catalog endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpCatalogTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RecyclingItemRepository recyclingItemRepository;
    @Autowired private JwtService jwtService;

    private String userToken;
    private String seededTitle;
    private Long item1Id;
    private Long item2Id;
    private Long item3Id;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole = findOrCreateRole("ROLE_USER");
        User user = createUser("cat_user_" + nonce, userRole);
        userToken = jwtService.generateAccessToken(user);

        // Item 1 – Electronics, $5
        seededTitle = "CatItem_Electronics_" + nonce;
        RecyclingItem item1 = new RecyclingItem();
        item1.setTitle(seededTitle);
        item1.setNormalizedTitle(seededTitle.toLowerCase());
        item1.setDescription("Electronics item for catalog tests");
        item1.setCategory("Electronics");
        item1.setItemCondition("GOOD");
        item1.setPrice(new BigDecimal("5.00"));
        item1.setCurrency("USD");
        item1.setSellerId(user.getId());
        item1.setActive(true);
        item1.setCreatedAt(LocalDateTime.now());
        item1.setUpdatedAt(LocalDateTime.now());
        item1 = recyclingItemRepository.save(item1);
        item1Id = item1.getId();

        // Item 2 – Electronics, $50
        RecyclingItem item2 = new RecyclingItem();
        item2.setTitle("CatItem_Mid_" + nonce);
        item2.setNormalizedTitle("catitem_mid_" + nonce);
        item2.setDescription("Mid-price item for catalog tests");
        item2.setCategory("Electronics");
        item2.setItemCondition("GOOD");
        item2.setPrice(new BigDecimal("50.00"));
        item2.setCurrency("USD");
        item2.setSellerId(user.getId());
        item2.setActive(true);
        item2.setCreatedAt(LocalDateTime.now());
        item2.setUpdatedAt(LocalDateTime.now());
        item2 = recyclingItemRepository.save(item2);
        item2Id = item2.getId();

        // Item 3 – Furniture, $500
        RecyclingItem item3 = new RecyclingItem();
        item3.setTitle("CatItem_Furniture_" + nonce);
        item3.setNormalizedTitle("catitem_furniture_" + nonce);
        item3.setDescription("Furniture item for catalog tests");
        item3.setCategory("Furniture");
        item3.setItemCondition("GOOD");
        item3.setPrice(new BigDecimal("500.00"));
        item3.setCurrency("USD");
        item3.setSellerId(user.getId());
        item3.setActive(true);
        item3.setCreatedAt(LocalDateTime.now());
        item3.setUpdatedAt(LocalDateTime.now());
        item3 = recyclingItemRepository.save(item3);
        item3Id = item3.getId();
    }

    // =========================================================================
    // 1. Keyword search
    // =========================================================================

    @Test
    void shouldSearchByKeywordOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/catalog/search?keyword=" + seededTitle,
                HttpMethod.GET, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        List<?> items = (List<?>) resp.getBody().get("items");
        assertThat(items).isNotNull().isNotEmpty();
        assertThat(resp.getBody().get("searchLogId")).isInstanceOf(Number.class);

        boolean found = items.stream()
                .filter(i -> i instanceof Map)
                .map(i -> (Map<?, ?>) i)
                .anyMatch(i -> seededTitle.equals(i.get("title")));
        assertThat(found).as("search result should contain the seeded item").isTrue();
    }

    // =========================================================================
    // 2. Category filter
    // =========================================================================

    @Test
    void shouldFilterByCategoryOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/catalog/search?category=Electronics",
                HttpMethod.GET, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        List<?> items = (List<?>) resp.getBody().get("items");
        assertThat(items).isNotNull().isNotEmpty();

        boolean allElectronics = items.stream()
                .filter(i -> i instanceof Map)
                .map(i -> (Map<?, ?>) i)
                .allMatch(i -> "Electronics".equals(i.get("category")));
        assertThat(allElectronics).as("all returned items should have category=Electronics").isTrue();
    }

    // =========================================================================
    // 3. Price-range filter
    // =========================================================================

    @Test
    void shouldFilterByPriceRangeOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/catalog/search?minPrice=10&maxPrice=100",
                HttpMethod.GET, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        List<?> items = (List<?>) resp.getBody().get("items");
        assertThat(items).isNotNull();

        // item2 ($50) should appear; item1 ($5) and item3 ($500) should not
        boolean hasMid = items.stream()
                .filter(i -> i instanceof Map)
                .map(i -> (Map<?, ?>) i)
                .anyMatch(i -> item2Id.toString().equals(String.valueOf(((Number) i.get("id")).longValue())));
        assertThat(hasMid).as("$50 item should appear in price range 10..100").isTrue();

        boolean hasLow = items.stream()
                .filter(i -> i instanceof Map)
                .map(i -> (Map<?, ?>) i)
                .anyMatch(i -> item1Id.toString().equals(String.valueOf(((Number) i.get("id")).longValue())));
        assertThat(hasLow).as("$5 item should NOT appear in price range 10..100").isFalse();
    }

    // =========================================================================
    // 4. No-match returns empty list
    // =========================================================================

    @Test
    void shouldReturnEmptyItemsForNoMatchOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/catalog/search?keyword=zzzzzNoMatch_abcxyz_12345",
                HttpMethod.GET, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        List<?> items = (List<?>) resp.getBody().get("items");
        assertThat(items).isNotNull().isEmpty();
    }

    // =========================================================================
    // 5. Get item by ID
    // =========================================================================

    @Test
    void shouldGetItemByIdOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/catalog/" + item1Id,
                HttpMethod.GET, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("id")).isNotNull();
        assertThat(resp.getBody().get("title")).isEqualTo(seededTitle);
        assertThat(resp.getBody().get("price")).isNotNull();
    }

    // =========================================================================
    // 6. 404 for missing item
    // =========================================================================

    @Test
    void shouldReturn404ForMissingItemOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/catalog/99999",
                HttpMethod.GET, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // =========================================================================
    // 7. Check-duplicate
    // =========================================================================

    @Test
    void shouldCheckDuplicateOverRealHttp() {
        Map<String, String> body = Map.of(
                "title", "New Unique Title Xyz_" + System.nanoTime(),
                "attributes", "something");

        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(userToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/catalog/check-duplicate",
                HttpMethod.POST, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("status")).isEqualTo("UNIQUE");
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
