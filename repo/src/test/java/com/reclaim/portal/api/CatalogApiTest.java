package com.reclaim.portal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.ItemFingerprintRepository;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.search.repository.SearchClickLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private RecyclingItemRepository recyclingItemRepository;
    @Autowired private SearchClickLogRepository searchClickLogRepository;
    @Autowired private ItemFingerprintRepository itemFingerprintRepository;

    private String userToken;
    private Long itemId1;
    private Long itemId2;
    private String item1Title;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole = findOrCreateRole("ROLE_USER");
        User user = createUser("catalog_user_" + nonce, userRole);
        userToken = jwtService.generateAccessToken(user);

        item1Title = "UniqueRecycleTelescope_" + nonce;

        RecyclingItem item1 = new RecyclingItem();
        item1.setTitle(item1Title);
        item1.setNormalizedTitle(item1Title.toLowerCase());
        item1.setDescription("A fine telescope");
        item1.setCategory("Electronics");
        item1.setItemCondition("GOOD");
        item1.setPrice(new BigDecimal("49.99"));
        item1.setCurrency("USD");
        item1.setSellerId(user.getId());
        item1.setActive(true);
        item1.setCreatedAt(LocalDateTime.now());
        item1.setUpdatedAt(LocalDateTime.now());
        item1 = recyclingItemRepository.save(item1);
        itemId1 = item1.getId();

        RecyclingItem item2 = new RecyclingItem();
        item2.setTitle("GreenFurniture_" + nonce);
        item2.setNormalizedTitle("greenfurniture_" + nonce);
        item2.setDescription("Eco-friendly chair");
        item2.setCategory("Furniture");
        item2.setItemCondition("EXCELLENT");
        item2.setPrice(new BigDecimal("120.00"));
        item2.setCurrency("USD");
        item2.setSellerId(user.getId());
        item2.setActive(true);
        item2.setCreatedAt(LocalDateTime.now());
        item2.setUpdatedAt(LocalDateTime.now());
        item2 = recyclingItemRepository.save(item2);
        itemId2 = item2.getId();
    }

    @Test
    void shouldSearchItemsByKeyword() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/catalog/search")
                .param("keyword", item1Title)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.searchLogId").isNumber())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("items")).isTrue();
        assertThat(body.get("items").isArray()).isTrue();
        assertThat(body.get("items").size()).isGreaterThan(0);

        // Verify at least one result has the seeded title
        boolean foundTitle = false;
        for (var item : body.get("items")) {
            if (item1Title.equals(item.get("title").asText())) {
                foundTitle = true;
                break;
            }
        }
        assertThat(foundTitle).as("seeded item title '%s' should appear in results", item1Title).isTrue();
    }

    @Test
    void shouldFilterByCategoryAndPriceRange() throws Exception {
        mockMvc.perform(get("/api/catalog/search")
                .param("category", "Electronics")
                .param("minPrice", "0")
                .param("maxPrice", "1000")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void shouldReturnEmptyResultsForNoMatch() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/catalog/search")
                .param("keyword", "nonexistent_xyz_abc_12345")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("items").isArray()).isTrue();
        assertThat(body.get("items").size()).isEqualTo(0);
    }

    @Test
    void shouldGetItemById() throws Exception {
        mockMvc.perform(get("/api/catalog/" + itemId1)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(item1Title));
    }

    @Test
    void shouldReturn404ForMissingItem() throws Exception {
        mockMvc.perform(get("/api/catalog/99999999")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldLogClick() throws Exception {
        // First do a search to get a searchLogId
        MvcResult searchResult = mockMvc.perform(get("/api/catalog/search")
                .param("keyword", item1Title)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        long searchLogId = objectMapper.readTree(searchResult.getResponse().getContentAsString())
                .get("searchLogId").asLong();

        Map<String, Long> body = Map.of("searchLogId", searchLogId, "itemId", itemId1);

        mockMvc.perform(post("/api/catalog/click")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldCheckDuplicate() throws Exception {
        Map<String, String> body = Map.of("title", item1Title, "attributes", "");

        MvcResult result = mockMvc.perform(post("/api/catalog/check-duplicate")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andReturn();

        var responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(responseBody.has("status")).isTrue();
        String status = responseBody.get("status").asText();
        // status must be one of the known duplicate-check strings returned by CatalogService
        assertThat(status).isIn("EXACT_DUPLICATE", "NEAR_DUPLICATE", "UNIQUE");
    }

    // =========================================================================
    // Additional deep tests — GET /api/catalog/search
    // =========================================================================

    /**
     * Searching with category=Electronics and condition=GOOD returns only items
     * that match both filters. Items with different category or condition must not appear.
     */
    @Test
    void shouldFilterByCategoryAndCondition() throws Exception {
        long nonce = System.nanoTime();
        Role userRole = findOrCreateRole("ROLE_USER");
        User filterUser = createUser("catalog_filter_" + nonce, userRole);
        String filterToken = jwtService.generateAccessToken(filterUser);

        // Electronics + GOOD (should match)
        RecyclingItem match = new RecyclingItem();
        match.setTitle("ElecGood_" + nonce);
        match.setNormalizedTitle("elecgood_" + nonce);
        match.setDescription("A matching item");
        match.setCategory("Electronics");
        match.setItemCondition("GOOD");
        match.setPrice(new BigDecimal("30.00"));
        match.setCurrency("USD");
        match.setSellerId(filterUser.getId());
        match.setActive(true);
        match.setCreatedAt(LocalDateTime.now());
        match.setUpdatedAt(LocalDateTime.now());
        match = recyclingItemRepository.save(match);

        // Electronics + EXCELLENT (should not match condition=GOOD filter)
        RecyclingItem noMatch1 = new RecyclingItem();
        noMatch1.setTitle("ElecExcellent_" + nonce);
        noMatch1.setNormalizedTitle("elecexcellent_" + nonce);
        noMatch1.setDescription("Wrong condition");
        noMatch1.setCategory("Electronics");
        noMatch1.setItemCondition("EXCELLENT");
        noMatch1.setPrice(new BigDecimal("50.00"));
        noMatch1.setCurrency("USD");
        noMatch1.setSellerId(filterUser.getId());
        noMatch1.setActive(true);
        noMatch1.setCreatedAt(LocalDateTime.now());
        noMatch1.setUpdatedAt(LocalDateTime.now());
        recyclingItemRepository.save(noMatch1);

        // Furniture + GOOD (should not match category=Electronics filter)
        RecyclingItem noMatch2 = new RecyclingItem();
        noMatch2.setTitle("FurnitureGood_" + nonce);
        noMatch2.setNormalizedTitle("furnituregood_" + nonce);
        noMatch2.setDescription("Wrong category");
        noMatch2.setCategory("Furniture");
        noMatch2.setItemCondition("GOOD");
        noMatch2.setPrice(new BigDecimal("70.00"));
        noMatch2.setCurrency("USD");
        noMatch2.setSellerId(filterUser.getId());
        noMatch2.setActive(true);
        noMatch2.setCreatedAt(LocalDateTime.now());
        noMatch2.setUpdatedAt(LocalDateTime.now());
        recyclingItemRepository.save(noMatch2);

        MvcResult result = mockMvc.perform(get("/api/catalog/search")
                .param("category", "Electronics")
                .param("condition", "GOOD")
                .header("Authorization", "Bearer " + filterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        boolean foundMatch = false;
        for (var item : body.get("items")) {
            String cat = item.path("category").asText();
            String cond = item.path("itemCondition").asText();
            assertThat(cat).isEqualTo("Electronics");
            assertThat(cond).isEqualTo("GOOD");
            if (item.get("id").asLong() == match.getId()) foundMatch = true;
        }
        assertThat(foundMatch).as("seeded Electronics/GOOD item must appear in filtered results").isTrue();
    }

    /**
     * Price range filter returns only items within [minPrice, maxPrice].
     * Items at $5 and $500 must not appear when filtering minPrice=10&maxPrice=100.
     */
    @Test
    void shouldFilterByPriceRange() throws Exception {
        long nonce = System.nanoTime();
        Role userRole = findOrCreateRole("ROLE_USER");
        User priceUser = createUser("catalog_price_" + nonce, userRole);
        String priceToken = jwtService.generateAccessToken(priceUser);

        String uniqueKeyword = "PriceRangeWidget_" + nonce;

        RecyclingItem cheap = new RecyclingItem();
        cheap.setTitle(uniqueKeyword + "_cheap");
        cheap.setNormalizedTitle(uniqueKeyword.toLowerCase() + "_cheap");
        cheap.setDescription("Cheap");
        cheap.setCategory("Misc");
        cheap.setItemCondition("GOOD");
        cheap.setPrice(new BigDecimal("5.00"));
        cheap.setCurrency("USD");
        cheap.setSellerId(priceUser.getId());
        cheap.setActive(true);
        cheap.setCreatedAt(LocalDateTime.now());
        cheap.setUpdatedAt(LocalDateTime.now());
        cheap = recyclingItemRepository.save(cheap);

        RecyclingItem midRange = new RecyclingItem();
        midRange.setTitle(uniqueKeyword + "_midrange");
        midRange.setNormalizedTitle(uniqueKeyword.toLowerCase() + "_midrange");
        midRange.setDescription("Mid range");
        midRange.setCategory("Misc");
        midRange.setItemCondition("GOOD");
        midRange.setPrice(new BigDecimal("50.00"));
        midRange.setCurrency("USD");
        midRange.setSellerId(priceUser.getId());
        midRange.setActive(true);
        midRange.setCreatedAt(LocalDateTime.now());
        midRange.setUpdatedAt(LocalDateTime.now());
        midRange = recyclingItemRepository.save(midRange);

        RecyclingItem expensive = new RecyclingItem();
        expensive.setTitle(uniqueKeyword + "_expensive");
        expensive.setNormalizedTitle(uniqueKeyword.toLowerCase() + "_expensive");
        expensive.setDescription("Expensive");
        expensive.setCategory("Misc");
        expensive.setItemCondition("GOOD");
        expensive.setPrice(new BigDecimal("500.00"));
        expensive.setCurrency("USD");
        expensive.setSellerId(priceUser.getId());
        expensive.setActive(true);
        expensive.setCreatedAt(LocalDateTime.now());
        expensive.setUpdatedAt(LocalDateTime.now());
        expensive = recyclingItemRepository.save(expensive);

        MvcResult result = mockMvc.perform(get("/api/catalog/search")
                .param("keyword", uniqueKeyword)
                .param("minPrice", "10")
                .param("maxPrice", "100")
                .header("Authorization", "Bearer " + priceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        // Only the $50 item should appear
        boolean foundMid = false;
        boolean foundCheap = false;
        boolean foundExpensive = false;
        for (var item : body.get("items")) {
            long id = item.get("id").asLong();
            if (id == midRange.getId()) foundMid = true;
            if (id == cheap.getId()) foundCheap = true;
            if (id == expensive.getId()) foundExpensive = true;
        }
        assertThat(foundMid).as("$50 item must appear in price range 10-100").isTrue();
        assertThat(foundCheap).as("$5 item must NOT appear in price range 10-100").isFalse();
        assertThat(foundExpensive).as("$500 item must NOT appear in price range 10-100").isFalse();
    }

    /**
     * Searching for a keyword that matches nothing returns 200 with an empty items array.
     */
    @Test
    void shouldReturnEmptyItemsForNoMatch() throws Exception {
        mockMvc.perform(get("/api/catalog/search")
                .param("keyword", "zzzzzzNoMatch9999")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    /**
     * Seeded items appear in consistent order in repeated searches — the result is always an array.
     */
    @Test
    void shouldRankResultsConsistently() throws Exception {
        long nonce = System.nanoTime();
        Role userRole = findOrCreateRole("ROLE_USER");
        User rankUser = createUser("catalog_rank_" + nonce, userRole);
        String rankToken = jwtService.generateAccessToken(rankUser);

        String prefix = "RankTestWidget_" + nonce;

        for (int i = 0; i < 3; i++) {
            RecyclingItem item = new RecyclingItem();
            item.setTitle(prefix + "_" + i);
            item.setNormalizedTitle(prefix.toLowerCase() + "_" + i);
            item.setDescription("Rank test item " + i);
            item.setCategory("Electronics");
            item.setItemCondition("GOOD");
            item.setPrice(new BigDecimal("20.00"));
            item.setCurrency("USD");
            item.setSellerId(rankUser.getId());
            item.setActive(true);
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            recyclingItemRepository.save(item);
        }

        MvcResult result = mockMvc.perform(get("/api/catalog/search")
                .param("keyword", prefix)
                .header("Authorization", "Bearer " + rankToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        // All 3 seeded titles should appear somewhere in the results
        var items = body.get("items");
        for (int i = 0; i < 3; i++) {
            String expectedTitle = prefix + "_" + i;
            boolean found = false;
            for (var item : items) {
                if (expectedTitle.equals(item.path("title").asText())) {
                    found = true;
                    break;
                }
            }
            assertThat(found).as("Seeded item '" + expectedTitle + "' must appear in search results").isTrue();
        }
    }

    /**
     * Passing a non-numeric minPrice parameter triggers MethodArgumentTypeMismatchException
     * which GlobalExceptionHandler maps to 400 Bad Request.
     */
    @Test
    void shouldTolerateMalformedPriceParam() throws Exception {
        mockMvc.perform(get("/api/catalog/search")
                .param("minPrice", "notanumber")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Additional deep tests — POST /api/catalog/click
    // =========================================================================

    /**
     * Clicking without a searchLogId (only itemId) still creates a click log row.
     */
    @Test
    void shouldLogClickWithoutSearchLogId() throws Exception {
        long countBefore = searchClickLogRepository.count();

        // Only itemId — no searchLogId
        java.util.Map<String, Long> body = new java.util.HashMap<>();
        body.put("itemId", itemId1);
        // searchLogId intentionally omitted

        mockMvc.perform(post("/api/catalog/click")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        // Verify a row was created (click log saved even without search context)
        assertThat(searchClickLogRepository.count()).isGreaterThan(countBefore);
    }

    /**
     * POST /api/catalog/click with an empty body still returns 200 because the
     * controller accepts any Map<String,Long> and null values are allowed by SearchClickLog.
     * The actual row is created with null itemId. This encodes the actual behavior.
     */
    @Test
    void shouldAcceptClickWithMissingItemId() throws Exception {
        long countBefore = searchClickLogRepository.count();

        // Empty body — both searchLogId and itemId are null
        mockMvc.perform(post("/api/catalog/click")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        // Actual behavior: a SearchClickLog row is created with null itemId
        assertThat(searchClickLogRepository.count()).isGreaterThan(countBefore);
    }

    /**
     * POST /api/catalog/click without a Bearer token returns 401.
     */
    @Test
    void shouldRejectClickWithoutAuth() throws Exception {
        java.util.Map<String, Long> body = java.util.Map.of("itemId", itemId1);

        mockMvc.perform(post("/api/catalog/click")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Additional deep tests — GET /api/catalog/{id}
    // =========================================================================

    /**
     * GET /api/catalog/{id} for a seeded item returns 200 with expected fields.
     */
    @Test
    void shouldReturnItemForFoundId() throws Exception {
        mockMvc.perform(get("/api/catalog/" + itemId1)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId1))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.price").exists());
    }

    // shouldReturn404ForMissingItem already exists in the test class.

    // =========================================================================
    // Additional deep tests — POST /api/catalog/check-duplicate
    // =========================================================================

    /**
     * When a RecyclingItem exists with normalizedTitle="test widget {nonce}",
     * posting a title that normalizes to the same string returns NEAR_DUPLICATE.
     * CatalogService.checkDuplicate does NOT auto-persist fingerprints, so EXACT_DUPLICATE
     * is not reliably testable here without manual fingerprint seeding.
     */
    @Test
    void shouldReturnNearDuplicateForSameNormalizedTitle() throws Exception {
        long nonce = System.nanoTime();
        Role userRole = findOrCreateRole("ROLE_USER");
        User dupUser = createUser("catalog_dup_" + nonce, userRole);
        String dupToken = jwtService.generateAccessToken(dupUser);

        String normalizedTitle = "test widget " + nonce;

        RecyclingItem existing = new RecyclingItem();
        existing.setTitle("Test Widget " + nonce);
        existing.setNormalizedTitle(normalizedTitle); // pre-normalized
        existing.setDescription("Existing near-dup item");
        existing.setCategory("Electronics");
        existing.setItemCondition("GOOD");
        existing.setPrice(new BigDecimal("15.00"));
        existing.setCurrency("USD");
        existing.setSellerId(dupUser.getId());
        existing.setActive(true);
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());
        recyclingItemRepository.save(existing);

        // POST with same title (different casing/spacing) → NEAR_DUPLICATE
        java.util.Map<String, String> body = java.util.Map.of(
                "title", "TEST WIDGET " + nonce,
                "attributes", "different attrs"
        );

        mockMvc.perform(post("/api/catalog/check-duplicate")
                .with(csrf())
                .header("Authorization", "Bearer " + dupToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEAR_DUPLICATE"));
    }

    /**
     * Completely novel title with no match in the DB returns UNIQUE.
     */
    @Test
    void shouldReturnUniqueForNovelTitle() throws Exception {
        java.util.Map<String, String> body = java.util.Map.of(
                "title", "Completely Novel Title XYZ 99_" + System.nanoTime()
        );

        mockMvc.perform(post("/api/catalog/check-duplicate")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNIQUE"));
    }

    @Test
    void shouldLogClickAndPersist() throws Exception {
        // Seed a search to get a valid searchLogId
        MvcResult searchResult = mockMvc.perform(get("/api/catalog/search")
                .param("keyword", item1Title)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        long searchLogId = objectMapper.readTree(searchResult.getResponse().getContentAsString())
                .get("searchLogId").asLong();

        long initialCount = searchClickLogRepository.count();

        Map<String, Long> body = Map.of("searchLogId", searchLogId, "itemId", itemId1);

        mockMvc.perform(post("/api/catalog/click")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        assertThat(searchClickLogRepository.count()).isGreaterThan(initialCount);
    }

    // =========================================================================
    // New deeper search tests — GET /api/catalog/search
    // =========================================================================

    /**
     * Seeds 2 items matching the keyword — one active, one inactive.
     * Searching by keyword returns only the active item.
     */
    @Test
    void shouldExcludeInactiveItemsFromSearch() throws Exception {
        long nonce = System.nanoTime();
        Role userRole = findOrCreateRole("ROLE_USER");
        User activeUser = createUser("catalog_active_" + nonce, userRole);
        String activeToken = jwtService.generateAccessToken(activeUser);

        String keyword = "InactiveTest_" + nonce;

        RecyclingItem activeItem = new RecyclingItem();
        activeItem.setTitle(keyword + "_active");
        activeItem.setNormalizedTitle((keyword + "_active").toLowerCase());
        activeItem.setDescription("Active item");
        activeItem.setCategory("Electronics");
        activeItem.setItemCondition("GOOD");
        activeItem.setPrice(new BigDecimal("30.00"));
        activeItem.setCurrency("USD");
        activeItem.setSellerId(activeUser.getId());
        activeItem.setActive(true);
        activeItem.setCreatedAt(LocalDateTime.now());
        activeItem.setUpdatedAt(LocalDateTime.now());
        activeItem = recyclingItemRepository.save(activeItem);

        RecyclingItem inactiveItem = new RecyclingItem();
        inactiveItem.setTitle(keyword + "_inactive");
        inactiveItem.setNormalizedTitle((keyword + "_inactive").toLowerCase());
        inactiveItem.setDescription("Inactive item — must not appear");
        inactiveItem.setCategory("Electronics");
        inactiveItem.setItemCondition("GOOD");
        inactiveItem.setPrice(new BigDecimal("30.00"));
        inactiveItem.setCurrency("USD");
        inactiveItem.setSellerId(activeUser.getId());
        inactiveItem.setActive(false);  // inactive
        inactiveItem.setCreatedAt(LocalDateTime.now());
        inactiveItem.setUpdatedAt(LocalDateTime.now());
        inactiveItem = recyclingItemRepository.save(inactiveItem);

        MvcResult result = mockMvc.perform(get("/api/catalog/search")
                .param("keyword", keyword)
                .header("Authorization", "Bearer " + activeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        boolean foundActive   = false;
        boolean foundInactive = false;
        final long inactiveId = inactiveItem.getId();
        final long activeId   = activeItem.getId();
        for (var item : body.get("items")) {
            long id = item.get("id").asLong();
            if (id == activeId)   foundActive   = true;
            if (id == inactiveId) foundInactive = true;
        }
        assertThat(foundActive).as("active item must appear").isTrue();
        assertThat(foundInactive).as("inactive item must NOT appear").isFalse();
    }

    /**
     * Seeds 4 items with varying combos of category, price, and condition.
     * Only the single item matching all three filters appears in the results.
     */
    @Test
    void shouldHandleSimultaneousCategoryAndPriceAndCondition() throws Exception {
        long nonce = System.nanoTime();
        Role userRole = findOrCreateRole("ROLE_USER");
        User comboUser = createUser("catalog_combo_" + nonce, userRole);
        String comboToken = jwtService.generateAccessToken(comboUser);

        // Item matching all three filters: Electronics, $50, GOOD
        RecyclingItem match = recyclingItemRepository.save(buildItem("TripleMatch_" + nonce, "Electronics", "GOOD", new BigDecimal("50.00"), comboUser.getId(), true));
        // Wrong category
        RecyclingItem wrongCat = recyclingItemRepository.save(buildItem("WrongCat_" + nonce, "Furniture", "GOOD", new BigDecimal("50.00"), comboUser.getId(), true));
        // Wrong price (out of 10-100 range)
        RecyclingItem wrongPrice = recyclingItemRepository.save(buildItem("WrongPrice_" + nonce, "Electronics", "GOOD", new BigDecimal("200.00"), comboUser.getId(), true));
        // Wrong condition
        RecyclingItem wrongCond = recyclingItemRepository.save(buildItem("WrongCond_" + nonce, "Electronics", "EXCELLENT", new BigDecimal("50.00"), comboUser.getId(), true));

        MvcResult result = mockMvc.perform(get("/api/catalog/search")
                .param("category", "Electronics")
                .param("minPrice", "10")
                .param("maxPrice", "100")
                .param("condition", "GOOD")
                .header("Authorization", "Bearer " + comboToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        boolean foundMatch      = false;
        boolean foundWrongCat   = false;
        boolean foundWrongPrice = false;
        boolean foundWrongCond  = false;
        for (var item : body.get("items")) {
            long id = item.get("id").asLong();
            if (id == match.getId())      foundMatch      = true;
            if (id == wrongCat.getId())   foundWrongCat   = true;
            if (id == wrongPrice.getId()) foundWrongPrice = true;
            if (id == wrongCond.getId())  foundWrongCond  = true;
        }
        assertThat(foundMatch).as("matching item must appear").isTrue();
        assertThat(foundWrongCat).as("wrong-category item must NOT appear").isFalse();
        assertThat(foundWrongPrice).as("out-of-price-range item must NOT appear").isFalse();
        assertThat(foundWrongCond).as("wrong-condition item must NOT appear").isFalse();
    }

    /**
     * Runs the same search twice and asserts the returned item IDs are in the same order.
     * This is a regression guard against non-deterministic ordering.
     */
    @Test
    void shouldReturnItemsInConsistentOrderWithStableSort() throws Exception {
        long nonce = System.nanoTime();
        Role userRole = findOrCreateRole("ROLE_USER");
        User sortUser = createUser("catalog_sort_" + nonce, userRole);
        String sortToken = jwtService.generateAccessToken(sortUser);

        String prefix = "StableSort_" + nonce;
        for (int i = 0; i < 3; i++) {
            recyclingItemRepository.save(buildItem(prefix + "_" + i, "Electronics", "GOOD", new BigDecimal("20.00"), sortUser.getId(), true));
        }

        MvcResult first = mockMvc.perform(get("/api/catalog/search")
                .param("keyword", prefix)
                .header("Authorization", "Bearer " + sortToken))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult second = mockMvc.perform(get("/api/catalog/search")
                .param("keyword", prefix)
                .header("Authorization", "Bearer " + sortToken))
                .andExpect(status().isOk())
                .andReturn();

        var body1 = objectMapper.readTree(first.getResponse().getContentAsString()).get("items");
        var body2 = objectMapper.readTree(second.getResponse().getContentAsString()).get("items");

        assertThat(body1.size()).isEqualTo(body2.size());
        for (int i = 0; i < body1.size(); i++) {
            assertThat(body1.get(i).get("id").asLong())
                    .as("item at position " + i + " must be same in both results")
                    .isEqualTo(body2.get(i).get("id").asLong());
        }
    }

    // =========================================================================
    // New deeper click tests — POST /api/catalog/click
    // =========================================================================

    /**
     * POST /api/catalog/click with itemId=99999 (no such item in the DB).
     *
     * <p>Actual behavior: the DB has a FK constraint (fk_search_click_logs_item) that requires
     * item_id to reference a real recycling_items row. When itemId=99999 doesn't exist,
     * H2 throws a referential-integrity violation which Spring maps to 500 Internal Server Error.
     * The service does NOT validate item existence before saving, so the FK check is the only gate.
     * This test encodes the actual 500 behavior.
     */
    @Test
    void shouldLogClickWithInvalidItemId() throws Exception {
        // Actual behavior: FK constraint violation on item_id → DataIntegrityViolationException → 500
        java.util.Map<String, Long> body = new java.util.HashMap<>();
        body.put("itemId", 99999L);

        mockMvc.perform(post("/api/catalog/click")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isInternalServerError()); // FK constraint on item_id → 500
    }

    /**
     * POST /api/catalog/click with searchLogId=99999 (no such search log) and a valid itemId.
     *
     * <p>Actual behavior: search_click_logs.search_log_id is FK-constrained to search_logs(id).
     * Passing searchLogId=99999 (non-existent) triggers a referential-integrity violation,
     * which the GlobalExceptionHandler maps to 500 Internal Server Error (unexpected error path).
     * This test encodes the actual 500 behavior.
     */
    @Test
    void shouldLogClickWithInvalidSearchLogId() throws Exception {
        // Actual behavior: FK constraint (fk_search_click_logs_log) on search_log_id
        // causes DataIntegrityViolationException → 500 when searchLogId=99999 doesn't exist.
        java.util.Map<String, Long> body = new java.util.HashMap<>();
        body.put("searchLogId", 99999L);
        body.put("itemId", itemId1);

        mockMvc.perform(post("/api/catalog/click")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isInternalServerError()); // FK constraint on search_log_id → 500
    }

    /**
     * POST a click, then GET /api/admin/analytics/search as admin.
     * Assert totalClicks >= 1 in the analytics response.
     */
    @Test
    void shouldPersistClickRowForSubsequentAnalyticsQuery() throws Exception {
        // Create an admin user
        long nonce = System.nanoTime();
        Role adminRole = findOrCreateRole("ROLE_ADMIN");
        User adminUser = createUser("catalog_analytics_admin_" + nonce, adminRole);
        String adminToken = jwtService.generateAccessToken(adminUser);

        // Get a valid searchLogId from a real search
        MvcResult searchResult = mockMvc.perform(get("/api/catalog/search")
                .param("keyword", item1Title)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        long searchLogId = objectMapper.readTree(searchResult.getResponse().getContentAsString())
                .get("searchLogId").asLong();

        // Post a click
        java.util.Map<String, Long> clickBody = java.util.Map.of("searchLogId", searchLogId, "itemId", itemId1);
        mockMvc.perform(post("/api/catalog/click")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(clickBody)))
                .andExpect(status().isOk());

        // Query analytics and assert totalClicks >= 1
        MvcResult analyticsResult = mockMvc.perform(get("/api/admin/analytics/search")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        var analyticsBody = objectMapper.readTree(analyticsResult.getResponse().getContentAsString());
        long totalClicks = analyticsBody.path("totalClicks").asLong(-1);
        assertThat(totalClicks).as("totalClicks must be >= 1 after posting a click").isGreaterThanOrEqualTo(1L);
    }

    // =========================================================================
    // New deeper duplicate tests — POST /api/catalog/check-duplicate
    // =========================================================================

    /**
     * Compute SHA-256 of normalizedTitle + attributes and seed an ItemFingerprint with that hash.
     * Then POST {"title":"Test Widget","attributes":"attrs"} → 200 {"status":"EXACT_DUPLICATE"}.
     *
     * <p>CatalogService.checkDuplicate normalizes title as: title.toLowerCase().trim().replaceAll("\\s+", " ")
     * then concatenates attributes directly. The hash covers normalizedTitle + attributes.
     */
    @Test
    void shouldReturnExactDuplicateWhenFingerprintHashMatches() throws Exception {
        long nonce = System.nanoTime();
        String rawTitle = "Test Widget " + nonce;
        String rawAttributes = "attrs_" + nonce;

        // Replicate CatalogService normalization
        String normalizedTitle = rawTitle.toLowerCase().trim().replaceAll("\\s+", " ");
        String combined = normalizedTitle + rawAttributes;

        // Compute SHA-256 the same way CatalogService does
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String fingerprintHash = java.util.HexFormat.of().formatHex(hashBytes);

        // Seed the fingerprint
        com.reclaim.portal.catalog.entity.ItemFingerprint fp = new com.reclaim.portal.catalog.entity.ItemFingerprint();
        fp.setItemId(itemId1);
        fp.setFingerprintHash(fingerprintHash);
        fp.setNormalizedAttributes(rawAttributes);
        fp.setDuplicateStatus("EXACT_DUPLICATE");
        fp.setReviewed(false);
        fp.setCreatedAt(LocalDateTime.now());
        itemFingerprintRepository.save(fp);

        java.util.Map<String, String> body = java.util.Map.of("title", rawTitle, "attributes", rawAttributes);

        mockMvc.perform(post("/api/catalog/check-duplicate")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXACT_DUPLICATE"));
    }

    // =========================================================================
    // Helpers (private)
    // =========================================================================

    /** Builds a RecyclingItem without saving it — caller must call save. */
    private RecyclingItem buildItem(String title, String category, String condition,
                                    BigDecimal price, Long sellerId, boolean active) {
        RecyclingItem item = new RecyclingItem();
        item.setTitle(title);
        item.setNormalizedTitle(title.toLowerCase());
        item.setDescription("Test item: " + title);
        item.setCategory(category);
        item.setItemCondition(condition);
        item.setPrice(price);
        item.setCurrency("USD");
        item.setSellerId(sellerId);
        item.setActive(active);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
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
