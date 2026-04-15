package com.reclaim.portal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.search.service.SearchService;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SearchApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private SearchService searchService;

    private String userToken;
    private String adminToken;
    private String seededTerm;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole  = findOrCreateRole("ROLE_USER");
        Role adminRole = findOrCreateRole("ROLE_ADMIN");

        User user  = createUser("search_api_user_"  + nonce, userRole);
        User admin = createUser("search_api_admin_" + nonce, adminRole);

        userToken  = jwtService.generateAccessToken(user);
        adminToken = jwtService.generateAccessToken(admin);

        // Seed a single search trend for basic coverage
        seededTerm = "trending_term_" + nonce;
        searchService.updateTrends(seededTerm);
    }

    // =========================================================================
    // Trending — shape and sort order
    // =========================================================================

    @Test
    void shouldReturnTrendingSearches() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/search/trending")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();

        // Seeded term must appear somewhere in the array
        mockMvc.perform(get("/api/search/trending")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.searchTerm == '" + seededTerm + "')]").exists());
    }

    /**
     * Seeds "widget" 3 times and "gadget" 1 time, then asserts:
     * - widget has searchCount >= 3
     * - gadget is present
     * - widget appears before gadget (descending count order)
     */
    @Test
    void shouldReturnTrendingSearchesInDescendingCountOrder() throws Exception {
        long nonce = System.nanoTime();
        String widgetTerm = "widget_" + nonce;
        String gadgetTerm = "gadget_" + nonce;

        searchService.updateTrends(widgetTerm);
        searchService.updateTrends(widgetTerm);
        searchService.updateTrends(widgetTerm);
        searchService.updateTrends(gadgetTerm);

        MvcResult result = mockMvc.perform(get("/api/search/trending")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        JsonNode trends = objectMapper.readTree(result.getResponse().getContentAsString());

        // Locate widget and gadget positions in the array
        int widgetIndex = -1;
        int gadgetIndex = -1;
        int widgetCount = 0;
        boolean gadgetFound = false;

        for (int i = 0; i < trends.size(); i++) {
            JsonNode node = trends.get(i);
            String term = node.path("searchTerm").asText();
            if (widgetTerm.equals(term)) {
                widgetIndex = i;
                widgetCount = node.path("searchCount").asInt();
            }
            if (gadgetTerm.equals(term)) {
                gadgetIndex = i;
                gadgetFound = true;
            }
        }

        assertThat(widgetIndex).as("widget term must be present in trending").isGreaterThanOrEqualTo(0);
        assertThat(widgetCount).as("widget searchCount must be >= 3").isGreaterThanOrEqualTo(3);
        assertThat(gadgetFound).as("gadget term must be present in trending").isTrue();
        assertThat(widgetIndex).as("widget (count=3) must come before gadget (count=1) in descending order")
                .isLessThan(gadgetIndex);
    }

    @Test
    void shouldReturnArrayForTrending() throws Exception {
        mockMvc.perform(get("/api/search/trending")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * Seeds 15 distinct terms. The repository method is findTop10ByOrderBySearchCountDesc,
     * so the endpoint must return at most 10 results.
     */
    @Test
    void shouldLimitTrendingResultsToTop10() throws Exception {
        long nonce = System.nanoTime();
        for (int i = 0; i < 15; i++) {
            searchService.updateTrends("limitterm_" + nonce + "_" + i);
        }

        MvcResult result = mockMvc.perform(get("/api/search/trending")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        JsonNode trends = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(trends.size())
                .as("/api/search/trending must return at most 10 results (findTop10)")
                .isLessThanOrEqualTo(10);
    }

    // =========================================================================
    // Autocomplete — deep payload verification
    // =========================================================================

    /**
     * Seeds two terms that share a common unique prefix and one unrelated term.
     * The autocomplete query uses the shared prefix so exactly the two matching
     * terms are returned (not the unrelated one).
     *
     * Term structure (nonce = System.nanoTime()):
     *   shared prefix : "actest{nonce}"
     *   term A        : "actest{nonce}big"
     *   term B        : "actest{nonce}small"
     *   unrelated     : "gadget{nonce}"
     *
     * Query string is "actest{nonce}" which is contained in A and B but not in the
     * gadget term, so the autocomplete (LIKE %q% via containsIgnoreCase) returns
     * exactly A and B.
     */
    @Test
    void shouldReturnAutocompleteSuggestions() throws Exception {
        long nonce = System.nanoTime();
        // Build terms so the shared prefix is a genuine substring of both
        String sharedPrefix = "actest" + nonce;
        String termA    = sharedPrefix + "big";
        String termB    = sharedPrefix + "small";
        String unrelated = "gadget" + nonce;

        searchService.updateTrends(termA);
        searchService.updateTrends(termB);
        searchService.updateTrends(unrelated);

        MvcResult result = mockMvc.perform(get("/api/search/autocomplete")
                .param("q", sharedPrefix)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode suggestions = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(suggestions.isArray()).isTrue();
        assertThat(suggestions.size())
                .as("Both termA and termB must be returned for sharedPrefix query")
                .isGreaterThanOrEqualTo(2);

        // Both matching terms must appear; unrelated term must not
        boolean foundA       = false;
        boolean foundB       = false;
        boolean foundUnrelated = false;
        for (JsonNode node : suggestions) {
            String s = node.isTextual() ? node.asText()
                     : (node.has("searchTerm") ? node.get("searchTerm").asText() : node.asText());
            if (termA.equals(s))       foundA = true;
            if (termB.equals(s))       foundB = true;
            if (unrelated.equals(s))   foundUnrelated = true;
        }
        assertThat(foundA).as("termA must be in autocomplete results").isTrue();
        assertThat(foundB).as("termB must be in autocomplete results").isTrue();
        assertThat(foundUnrelated).as("unrelated gadget term must NOT appear for sharedPrefix query").isFalse();

        // Also verify via jsonPath hasItem matcher
        mockMvc.perform(get("/api/search/autocomplete")
                .param("q", sharedPrefix)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*]").value(hasItem(termA)))
                .andExpect(jsonPath("$[*]").value(hasItem(termB)));
    }

    /**
     * A search for a term with no matching trends must return an empty array.
     */
    @Test
    void shouldReturnEmptyAutocompleteForNoMatch() throws Exception {
        mockMvc.perform(get("/api/search/autocomplete")
                .param("q", "zzzqqq_no_match_ever_" + System.nanoTime())
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturn200ForAutocompleteWithNoMatch() throws Exception {
        mockMvc.perform(get("/api/search/autocomplete")
                .param("q", "zzzqqq_no_match")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldRequireAuthForAutocomplete() throws Exception {
        mockMvc.perform(get("/api/search/autocomplete")
                .param("q", "something"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Cross-feature: catalog search → analytics + trending integration
    // =========================================================================

    /**
     * Triggers a catalog search via GET /api/catalog/search, then checks that
     * GET /api/admin/analytics/search reflects at least 1 total search and that
     * the recent searches list includes the searched keyword.
     */
    @Test
    void shouldIncludeSearchLogInAnalyticsAfterCatalogSearch() throws Exception {
        long nonce = System.nanoTime();
        String keyword = "analyticsterm_" + nonce;

        // Trigger a catalog search (logs to search_logs table)
        mockMvc.perform(get("/api/catalog/search")
                .param("keyword", keyword)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // Fetch admin analytics
        MvcResult analyticsResult = mockMvc.perform(get("/api/admin/analytics/search")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode analytics = objectMapper.readTree(analyticsResult.getResponse().getContentAsString());

        long totalSearches = analytics.path("totalSearches").asLong();
        assertThat(totalSearches).as("totalSearches must be >= 1 after a catalog search").isGreaterThanOrEqualTo(1);

        // recentSearches should contain the keyword we just searched
        JsonNode recentSearches = analytics.path("recentSearches");
        assertThat(recentSearches.isArray()).isTrue();
        boolean foundKeyword = false;
        for (JsonNode entry : recentSearches) {
            String term = entry.path("searchTerm").asText();
            if (keyword.equals(term)) {
                foundKeyword = true;
                break;
            }
        }
        assertThat(foundKeyword)
                .as("recentSearches in analytics must include the just-searched keyword: " + keyword)
                .isTrue();
    }

    // =========================================================================
    // Auth guard
    // =========================================================================

    /**
     * GET /api/search/trending without authentication must return 401.
     */
    @Test
    void shouldRequireAuthForTrending() throws Exception {
        mockMvc.perform(get("/api/search/trending"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Calls GET /api/catalog/search with a new term and then verifies that
     * /api/search/trending includes that term (catalog search updates trends via SearchService).
     */
    @Test
    void shouldLogSearchCallUpdatesTrending() throws Exception {
        long nonce = System.nanoTime();
        String newTerm = "newterm_" + nonce;

        // Perform catalog search — the CatalogService calls searchService.updateTrends(keyword)
        mockMvc.perform(get("/api/catalog/search")
                .param("keyword", newTerm)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // Now the term should appear in trending
        mockMvc.perform(get("/api/search/trending")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.searchTerm == '" + newTerm + "')]").exists());
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
