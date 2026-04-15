package com.reclaim.portal.web;

import com.reclaim.portal.appeals.entity.Appeal;
import com.reclaim.portal.appeals.repository.AppealRepository;
import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.contracts.entity.ContractInstance;
import com.reclaim.portal.contracts.entity.ContractTemplate;
import com.reclaim.portal.contracts.entity.ContractTemplateVersion;
import com.reclaim.portal.contracts.repository.ContractInstanceRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateVersionRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for page controllers — verifies route resolution, model population,
 * and template rendering for critical user flows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserPageControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ContractInstanceRepository contractInstanceRepository;
    @Autowired private ContractTemplateRepository contractTemplateRepository;
    @Autowired private ContractTemplateVersionRepository contractTemplateVersionRepository;
    @Autowired private AppealRepository appealRepository;
    @Autowired private RecyclingItemRepository recyclingItemRepository;
    @Autowired private SearchService searchService;

    private User testUser;
    private String userToken;
    private Order testOrder;
    private Long templateVersionId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_USER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        testUser = new User();
        testUser.setUsername("pagetest_user_" + nonce);
        testUser.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        testUser.setEnabled(true);
        testUser.setLocked(false);
        testUser.setForcePasswordReset(false);
        testUser.setFailedAttempts(0);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
        testUser.setRoles(new HashSet<>(Set.of(userRole)));
        testUser = userRepository.save(testUser);
        userToken = jwtService.generateAccessToken(testUser);

        // Create an appointment and order for detail/model tests
        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(5));
        apt.setStartTime("10:00");
        apt.setEndTime("10:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(5);
        apt.setSlotsBooked(1);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        testOrder = new Order();
        testOrder.setUserId(testUser.getId());
        testOrder.setAppointmentId(apt.getId());
        testOrder.setOrderStatus("PENDING_CONFIRMATION");
        testOrder.setAppointmentType("PICKUP");
        testOrder.setRescheduleCount(0);
        testOrder.setCurrency("USD");
        testOrder.setTotalPrice(new BigDecimal("25.00"));
        testOrder.setCreatedAt(LocalDateTime.now());
        testOrder.setUpdatedAt(LocalDateTime.now());
        testOrder = orderRepository.save(testOrder);

        // Create a template + version for contract tests
        ContractTemplate tpl = new ContractTemplate();
        tpl.setName("Test Template " + nonce);
        tpl.setActive(true);
        tpl.setCreatedBy(testUser.getId());
        tpl.setCreatedAt(LocalDateTime.now());
        tpl.setUpdatedAt(LocalDateTime.now());
        tpl = contractTemplateRepository.save(tpl);

        ContractTemplateVersion ver = new ContractTemplateVersion();
        ver.setTemplateId(tpl.getId());
        ver.setVersionNumber(1);
        ver.setContent("Contract content");
        ver.setCreatedBy(testUser.getId());
        ver.setCreatedAt(LocalDateTime.now());
        ver = contractTemplateVersionRepository.save(ver);
        templateVersionId = ver.getId();
    }

    // ── Route + view resolution ─────────────────────────────────

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnDashboardPage() throws Exception {
        mockMvc.perform(get("/user/dashboard"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/dashboard"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnSearchPage() throws Exception {
        mockMvc.perform(get("/user/search"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/search"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnOrdersPage() throws Exception {
        mockMvc.perform(get("/user/orders"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/orders"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldRedirectRootToUserDashboard() throws Exception {
        mockMvc.perform(get("/"))
               .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnReviewsPage() throws Exception {
        mockMvc.perform(get("/user/reviews"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/reviews"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnContractsPage() throws Exception {
        mockMvc.perform(get("/user/contracts"))
               .andExpect(status().isOk())
               .andExpect(view().name("contract/list"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnCreateOrderPage() throws Exception {
        mockMvc.perform(get("/user/orders/create"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/create-order"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnCreateReviewPage() throws Exception {
        mockMvc.perform(get("/user/reviews/create"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/create-review"));
    }

    // ── Search with correct parameter name ──────────────────────

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnSearchResultsWithKeywordParam() throws Exception {
        // Controller uses @RequestParam "keyword", not "q"
        mockMvc.perform(get("/user/search")
                   .param("keyword", "laptop")
                   .param("category", "Electronics")
                   .param("minPrice", "10")
                   .param("maxPrice", "500"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/search"))
               .andExpect(model().attributeExists("results", "categories", "trendingTerms"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnSearchPageWithCategoryFilterOnly() throws Exception {
        // Filter-only search (no keyword) should still execute
        mockMvc.perform(get("/user/search")
                   .param("category", "Electronics"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/search"))
               .andExpect(model().attributeExists("results"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnSearchPageWithoutResultsWhenNoFilters() throws Exception {
        // No filters → no search executed → no "results" attribute
        mockMvc.perform(get("/user/search"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/search"))
               .andExpect(model().attributeExists("categories", "trendingTerms"))
               .andExpect(model().attributeDoesNotExist("results"));
    }

    // ── Order detail with model population ──────────────────────

    @Test
    void shouldPopulateOrderDetailModel() throws Exception {
        // Use real JWT token so resolveUserId finds the user in DB
        MvcResult result = mockMvc.perform(get("/user/orders/" + testOrder.getId())
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("user/order-detail"))
               .andExpect(model().attributeExists("order", "items", "logs"))
               .andReturn();

        // Verify the order model attribute is the correct order
        Order modelOrder = (Order) result.getModelAndView().getModel().get("order");
        assertThat(modelOrder.getId()).isEqualTo(testOrder.getId());
    }

    // ── Contract detail with model + inline JS ──────────────────

    @Test
    void shouldPopulateContractDetailModel() throws Exception {
        // Create a contract instance for this user
        ContractInstance contract = new ContractInstance();
        contract.setOrderId(testOrder.getId());
        contract.setTemplateVersionId(templateVersionId); // may not exist but entity allows it
        contract.setUserId(testUser.getId());
        contract.setContractStatus("INITIATED");
        contract.setRenderedContent("Test contract content");
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusDays(90));
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contract = contractInstanceRepository.save(contract);

        MvcResult result = mockMvc.perform(get("/user/contracts/" + contract.getId())
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("contract/detail"))
               .andExpect(model().attributeExists("contract"))
               .andReturn();

        ContractInstance modelContract = (ContractInstance) result.getModelAndView().getModel().get("contract");
        assertThat(modelContract.getId()).isEqualTo(contract.getId());
    }

    @Test
    void shouldPopulateContractSignModel() throws Exception {
        ContractInstance contract = new ContractInstance();
        contract.setOrderId(testOrder.getId());
        contract.setTemplateVersionId(templateVersionId);
        contract.setUserId(testUser.getId());
        contract.setContractStatus("CONFIRMED");
        contract.setRenderedContent("Sign this");
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusDays(90));
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contract = contractInstanceRepository.save(contract);

        // The sign page needs th:inline="javascript" to resolve /*[[${contract.id}]]*/
        // If this renders without error, the inlining is working
        mockMvc.perform(get("/user/contracts/" + contract.getId() + "/sign")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("contract/sign"))
               .andExpect(model().attributeExists("contract"));
    }

    @Test
    void shouldRedirectSignPageWhenContractNotConfirmed() throws Exception {
        // Create a contract in INITIATED state (not yet CONFIRMED)
        ContractInstance contract = new ContractInstance();
        contract.setOrderId(testOrder.getId());
        contract.setTemplateVersionId(templateVersionId);
        contract.setUserId(testUser.getId());
        contract.setContractStatus("INITIATED");
        contract.setRenderedContent("Not yet confirmed");
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusDays(90));
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contract = contractInstanceRepository.save(contract);

        // Should redirect back to contract detail instead of showing sign page
        mockMvc.perform(get("/user/contracts/" + contract.getId() + "/sign")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/user/contracts/" + contract.getId()));
    }

    @Test
    void shouldRedirectSignPageWhenContractAlreadySigned() throws Exception {
        ContractInstance contract = new ContractInstance();
        contract.setOrderId(testOrder.getId());
        contract.setTemplateVersionId(templateVersionId);
        contract.setUserId(testUser.getId());
        contract.setContractStatus("SIGNED");
        contract.setRenderedContent("Already signed");
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusDays(90));
        contract.setSignedAt(LocalDateTime.now());
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contract = contractInstanceRepository.save(contract);

        mockMvc.perform(get("/user/contracts/" + contract.getId() + "/sign")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/user/contracts/" + contract.getId()));
    }

    // ── Admin pages ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void shouldReturnStrategiesPage() throws Exception {
        mockMvc.perform(get("/admin/strategies"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/strategies"))
               .andExpect(model().attributeExists("strategies"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void shouldReturnTemplatesPage() throws Exception {
        mockMvc.perform(get("/admin/templates"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/templates"))
               .andExpect(model().attributeExists("templates"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void shouldPopulateAdminDashboardStats() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/dashboard"))
               .andExpect(model().attributeExists("totalUsers", "totalOrders", "totalSearches"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void shouldPopulateAnalyticsModel() throws Exception {
        mockMvc.perform(get("/admin/analytics"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/analytics"))
               .andExpect(model().attributeExists("totalSearches", "topTerms"));
    }

    // ── Reviewer pages ──────────────────────────────────────────

    @Test
    @WithMockUser(username = "reviewer", roles = {"REVIEWER"})
    void shouldPopulateReviewerDashboardStats() throws Exception {
        mockMvc.perform(get("/reviewer/dashboard"))
               .andExpect(status().isOk())
               .andExpect(view().name("reviewer/dashboard"))
               .andExpect(model().attributeExists("queueCount", "acceptedCount", "completedCount"));
    }

    @Test
    @WithMockUser(username = "reviewer", roles = {"REVIEWER"})
    void shouldPopulateReviewerQueueModel() throws Exception {
        mockMvc.perform(get("/reviewer/queue"))
               .andExpect(status().isOk())
               .andExpect(view().name("reviewer/queue"))
               .andExpect(model().attributeExists("queue"));
    }

    // ── Appeals pages ────────────────────────────────────────────────────────

    /**
     * GET /user/appeals with a real JWT token must return the appeals view
     * with a non-null list model attribute.
     */
    @Test
    void shouldReturnAppealsPage() throws Exception {
        mockMvc.perform(get("/user/appeals")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("user/appeals"))
               .andExpect(model().attributeExists("appeals"));

        // Tighten: the attribute must be a List (even if empty)
        MvcResult result = mockMvc.perform(get("/user/appeals")
                .header("Authorization", "Bearer " + userToken))
               .andReturn();
        Object appeals = result.getModelAndView().getModel().get("appeals");
        assertThat(appeals).isInstanceOf(java.util.List.class);
    }

    /**
     * GET /user/appeals/create?orderId=123 must return the create-appeal view
     * with the orderId model attribute set to 123.
     */
    @Test
    void shouldReturnCreateAppealPage() throws Exception {
        MvcResult result = mockMvc.perform(get("/user/appeals/create")
                .param("orderId", "123")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("user/create-appeal"))
               .andExpect(model().attributeExists("orderId"))
               .andReturn();

        Object orderId = result.getModelAndView().getModel().get("orderId");
        assertThat(orderId).isEqualTo(123L);
    }

    /**
     * GET /user/appeals/{id} for an appeal owned by testUser must return 200
     * with the appeal, evidence, and outcome model attributes.
     */
    @Test
    void shouldReturnAppealDetailPageForOwner() throws Exception {
        // Create an appeal owned by testUser
        Appeal appeal = new Appeal();
        appeal.setOrderId(testOrder.getId());
        appeal.setAppellantId(testUser.getId());
        appeal.setAppealStatus("OPEN");
        appeal.setReason("Test reason");
        appeal.setCreatedAt(LocalDateTime.now());
        appeal.setUpdatedAt(LocalDateTime.now());
        appeal = appealRepository.save(appeal);

        MvcResult result = mockMvc.perform(get("/user/appeals/" + appeal.getId())
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("user/appeal-detail"))
               .andExpect(model().attributeExists("appeal", "evidence", "outcome"))
               .andReturn();

        Appeal modelAppeal = (Appeal) result.getModelAndView().getModel().get("appeal");
        assertThat(modelAppeal.getId()).isEqualTo(appeal.getId());
    }

    /**
     * GET /user/appeals/{id} as a stranger (different user) must return 4xx
     * because the appeal belongs to userA and userB has no access.
     */
    @Test
    void shouldDenyAppealDetailForStranger() throws Exception {
        // Create a second user (the "stranger")
        long nonce2 = System.nanoTime();
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_USER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        User stranger = new User();
        stranger.setUsername("stranger_" + nonce2);
        stranger.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        stranger.setEnabled(true);
        stranger.setLocked(false);
        stranger.setForcePasswordReset(false);
        stranger.setFailedAttempts(0);
        stranger.setCreatedAt(LocalDateTime.now());
        stranger.setUpdatedAt(LocalDateTime.now());
        stranger.setRoles(new HashSet<>(Set.of(userRole)));
        stranger = userRepository.save(stranger);
        String strangerToken = jwtService.generateAccessToken(stranger);

        // Appeal owned by testUser
        Appeal appeal = new Appeal();
        appeal.setOrderId(testOrder.getId());
        appeal.setAppellantId(testUser.getId());
        appeal.setAppealStatus("OPEN");
        appeal.setReason("Owned by testUser");
        appeal.setCreatedAt(LocalDateTime.now());
        appeal.setUpdatedAt(LocalDateTime.now());
        appeal = appealRepository.save(appeal);

        // Stranger should not be able to access it
        mockMvc.perform(get("/user/appeals/" + appeal.getId())
                .header("Authorization", "Bearer " + strangerToken))
               .andExpect(status().is4xxClientError());
    }

    // ── Contracts page with status filter ────────────────────────────────────

    /**
     * GET /user/contracts?status=INITIATED must return 200 and a filtered
     * contracts list (even if empty — the key is the model attribute exists and is a List).
     */
    @Test
    void shouldReturnContractsPageWithStatusFilter() throws Exception {
        MvcResult result = mockMvc.perform(get("/user/contracts")
                .param("status", "INITIATED")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("contract/list"))
               .andExpect(model().attributeExists("contracts"))
               .andReturn();

        Object contracts = result.getModelAndView().getModel().get("contracts");
        assertThat(contracts).isInstanceOf(java.util.List.class);
    }

    // ── Create order page ────────────────────────────────────────────────────

    /**
     * GET /user/orders/create must return 200 and resolve the create-order view.
     * Verifies the route works with JWT authentication.
     */
    @Test
    void shouldReturnCreateOrderPageWithItemIds() throws Exception {
        mockMvc.perform(get("/user/orders/create")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("user/create-order"));
    }

    // ── Contract print page ──────────────────────────────────────────────────

    /**
     * GET /user/contracts/{id}/print for a contract owned by testUser must return 200,
     * view "contract/print", and model attribute "contract".
     */
    @Test
    void shouldReturnPrintContractPage() throws Exception {
        ContractInstance contract = new ContractInstance();
        contract.setOrderId(testOrder.getId());
        contract.setTemplateVersionId(templateVersionId);
        contract.setUserId(testUser.getId());
        contract.setContractStatus("SIGNED");
        contract.setRenderedContent("Printable contract content");
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusDays(90));
        contract.setSignedAt(LocalDateTime.now());
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contract = contractInstanceRepository.save(contract);

        MvcResult result = mockMvc.perform(get("/user/contracts/" + contract.getId() + "/print")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("contract/print"))
               .andExpect(model().attributeExists("contract"))
               .andReturn();

        ContractInstance modelContract =
                (ContractInstance) result.getModelAndView().getModel().get("contract");
        assertThat(modelContract.getId()).isEqualTo(contract.getId());
    }

    // ── Reviews page depth assertion ─────────────────────────────────────────

    /**
     * GET /user/reviews must return 200 and populate the "reviews" model attribute
     * as a List (tightens the existing shallow route-only check).
     */
    @Test
    void shouldReturnReviewsPageWithReviewsAttribute() throws Exception {
        MvcResult result = mockMvc.perform(get("/user/reviews")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("user/reviews"))
               .andExpect(model().attributeExists("reviews"))
               .andReturn();

        Object reviews = result.getModelAndView().getModel().get("reviews");
        assertThat(reviews).isInstanceOf(java.util.List.class);
    }

    // ── Dashboard counts from seeded orders ──────────────────────────────────

    /**
     * Seeds 3 orders with statuses PENDING_CONFIRMATION, ACCEPTED, and COMPLETED for the
     * test user, then asserts that /user/dashboard populates orderCount, pendingOrders,
     * completedOrders, and recentOrders with correct values.
     */
    @Test
    void shouldPopulateDashboardCountsFromSeededOrders() throws Exception {
        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(5));
        apt.setStartTime("10:00");
        apt.setEndTime("10:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(5);
        apt.setSlotsBooked(0);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        for (String status : java.util.List.of("PENDING_CONFIRMATION", "ACCEPTED", "COMPLETED")) {
            Order o = new Order();
            o.setUserId(testUser.getId());
            o.setAppointmentId(apt.getId());
            o.setOrderStatus(status);
            o.setAppointmentType("PICKUP");
            o.setRescheduleCount(0);
            o.setCurrency("USD");
            o.setTotalPrice(new BigDecimal("10.00"));
            o.setCreatedAt(LocalDateTime.now());
            o.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(o);
        }

        MvcResult result = mockMvc.perform(get("/user/dashboard")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("user/dashboard"))
               .andReturn();

        var model = result.getModelAndView().getModel();

        // orderCount: includes the existing testOrder from setUp + 3 seeded = 4
        Object orderCountObj = model.get("orderCount");
        assertThat(orderCountObj).isInstanceOf(Number.class);
        int orderCount = ((Number) orderCountObj).intValue();
        assertThat(orderCount).isGreaterThanOrEqualTo(3);

        // pendingOrders: at least 1 PENDING_CONFIRMATION
        Object pendingObj = model.get("pendingOrders");
        assertThat(pendingObj).isInstanceOf(Number.class);
        assertThat(((Number) pendingObj).longValue()).isGreaterThanOrEqualTo(1L);

        // completedOrders: at least 1 COMPLETED
        Object completedObj = model.get("completedOrders");
        assertThat(completedObj).isInstanceOf(Number.class);
        assertThat(((Number) completedObj).longValue()).isGreaterThanOrEqualTo(1L);

        // recentOrders is a List with at most 5 items
        Object recentObj = model.get("recentOrders");
        assertThat(recentObj).isInstanceOf(java.util.List.class);
        assertThat(((java.util.List<?>) recentObj).size()).isLessThanOrEqualTo(5);
    }

    // ── Search page — results and trending ───────────────────────────────────

    /**
     * Seeds a RecyclingItem and a trending search term. GET /user/search?keyword=... returns 200
     * with model attributes "results", "categories", and "trendingTerms" all populated.
     */
    @Test
    void shouldPopulateSearchPageWithResultsAndTrending() throws Exception {
        long nonce = System.nanoTime();

        // Seed a recycling item
        RecyclingItem item = new RecyclingItem();
        item.setTitle("SearchPageTest_" + nonce);
        item.setNormalizedTitle("searchpagetest_" + nonce);
        item.setDescription("A seeded search page item");
        item.setCategory("Electronics");
        item.setItemCondition("GOOD");
        item.setPrice(new BigDecimal("25.00"));
        item.setCurrency("USD");
        item.setSellerId(testUser.getId());
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item = recyclingItemRepository.save(item);

        // Seed trending terms
        String seedTerm = "SearchPageTrend_" + nonce;
        searchService.updateTrends(seedTerm);
        searchService.updateTrends(seedTerm);

        MvcResult result = mockMvc.perform(get("/user/search")
                .param("keyword", "SearchPageTest_" + nonce)
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("user/search"))
               .andExpect(model().attributeExists("results", "categories", "trendingTerms"))
               .andReturn();

        var model = result.getModelAndView().getModel();

        // results must contain the seeded item
        @SuppressWarnings("unchecked")
        java.util.List<RecyclingItem> results = (java.util.List<RecyclingItem>) model.get("results");
        assertThat(results).isNotNull();
        final Long seededItemId = item.getId();
        boolean foundItem = results.stream().anyMatch(r -> r.getId().equals(seededItemId));
        assertThat(foundItem).as("Seeded item must appear in /user/search results").isTrue();

        // categories must be a non-null list
        Object categories = model.get("categories");
        assertThat(categories).isInstanceOf(java.util.List.class);

        // trendingTerms must be a non-null list
        Object trendingTerms = model.get("trendingTerms");
        assertThat(trendingTerms).isInstanceOf(java.util.List.class);
    }

    // ── Contracts page with status filter (deeper assertions) ─────────────────

    /**
     * Seeds 2 contracts: one INITIATED and one CONFIRMED.
     * GET /user/contracts?status=INITIATED returns a list of size 1 with the INITIATED contract.
     */
    @Test
    void shouldFilterContractsByWorkflowStatus() throws Exception {
        ContractInstance c1 = new ContractInstance();
        c1.setOrderId(testOrder.getId());
        c1.setTemplateVersionId(templateVersionId);
        c1.setUserId(testUser.getId());
        c1.setContractStatus("INITIATED");
        c1.setRenderedContent("INITIATED contract");
        c1.setStartDate(LocalDate.now());
        c1.setEndDate(LocalDate.now().plusDays(90));
        c1.setCreatedAt(LocalDateTime.now());
        c1.setUpdatedAt(LocalDateTime.now());
        c1 = contractInstanceRepository.save(c1);

        ContractInstance c2 = new ContractInstance();
        c2.setOrderId(testOrder.getId());
        c2.setTemplateVersionId(templateVersionId);
        c2.setUserId(testUser.getId());
        c2.setContractStatus("CONFIRMED");
        c2.setRenderedContent("CONFIRMED contract");
        c2.setStartDate(LocalDate.now());
        c2.setEndDate(LocalDate.now().plusDays(90));
        c2.setCreatedAt(LocalDateTime.now());
        c2.setUpdatedAt(LocalDateTime.now());
        contractInstanceRepository.save(c2);

        MvcResult result = mockMvc.perform(get("/user/contracts")
                .param("status", "INITIATED")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("contract/list"))
               .andReturn();

        @SuppressWarnings("unchecked")
        java.util.List<ContractInstance> contracts =
                (java.util.List<ContractInstance>) result.getModelAndView().getModel().get("contracts");
        assertThat(contracts).isNotNull();
        // All returned contracts must have contractStatus or displayStatus == INITIATED
        for (ContractInstance c : contracts) {
            assertThat(c.getContractStatus()).isEqualTo("INITIATED");
        }
        // The seeded INITIATED contract must appear
        final Long c1Id = c1.getId();
        assertThat(contracts.stream().anyMatch(c -> c.getId().equals(c1Id))).isTrue();
    }

    /**
     * A signed contract with endDate in 10 days has displayStatus EXPIRING_SOON (default 30-day window).
     * GET /user/contracts?status=EXPIRING_SOON must include this contract.
     */
    @Test
    void shouldFilterContractsByDisplayStatus() throws Exception {
        ContractInstance expiring = new ContractInstance();
        expiring.setOrderId(testOrder.getId());
        expiring.setTemplateVersionId(templateVersionId);
        expiring.setUserId(testUser.getId());
        expiring.setContractStatus("SIGNED");
        expiring.setRenderedContent("Expiring soon contract");
        expiring.setStartDate(LocalDate.now().minusDays(60));
        // endDate in 10 days < expiringSoonDays=30 → displayStatus EXPIRING_SOON
        expiring.setEndDate(LocalDate.now().plusDays(10));
        expiring.setSignedAt(LocalDateTime.now().minusDays(60));
        expiring.setCreatedAt(LocalDateTime.now());
        expiring.setUpdatedAt(LocalDateTime.now());
        expiring = contractInstanceRepository.save(expiring);

        MvcResult result = mockMvc.perform(get("/user/contracts")
                .param("status", "EXPIRING_SOON")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("contract/list"))
               .andReturn();

        @SuppressWarnings("unchecked")
        java.util.List<ContractInstance> contracts =
                (java.util.List<ContractInstance>) result.getModelAndView().getModel().get("contracts");
        assertThat(contracts).isNotNull();
        final Long expiringId = expiring.getId();
        boolean foundExpiring = contracts.stream().anyMatch(c -> c.getId().equals(expiringId));
        assertThat(foundExpiring).as("Contract with endDate in 10 days must appear under EXPIRING_SOON filter").isTrue();
    }

    // ── Contract print page — deeper assertions ───────────────────────────────

    /**
     * GET /user/contracts/{id}/print for a SIGNED contract returns 200,
     * view "contract/print", and the model contract has contractStatus==SIGNED and signedAt non-null.
     */
    @Test
    void shouldReturnPrintPageForOwnerWithSignatureInfo() throws Exception {
        ContractInstance signed = new ContractInstance();
        signed.setOrderId(testOrder.getId());
        signed.setTemplateVersionId(templateVersionId);
        signed.setUserId(testUser.getId());
        signed.setContractStatus("SIGNED");
        signed.setRenderedContent("Signed printable content");
        signed.setStartDate(LocalDate.now());
        signed.setEndDate(LocalDate.now().plusDays(90));
        signed.setSignedAt(LocalDateTime.now());
        signed.setCreatedAt(LocalDateTime.now());
        signed.setUpdatedAt(LocalDateTime.now());
        signed = contractInstanceRepository.save(signed);

        MvcResult result = mockMvc.perform(get("/user/contracts/" + signed.getId() + "/print")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("contract/print"))
               .andExpect(model().attributeExists("contract"))
               .andReturn();

        ContractInstance modelContract =
                (ContractInstance) result.getModelAndView().getModel().get("contract");
        assertThat(modelContract.getContractStatus()).isEqualTo("SIGNED");
        assertThat(modelContract.getSignedAt()).isNotNull();
    }

    /**
     * A stranger (different user) attempting to print another user's contract gets 4xx.
     */
    @Test
    void shouldDenyPrintForStranger() throws Exception {
        long nonce = System.nanoTime();
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_USER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        User stranger = new User();
        stranger.setUsername("print_stranger_" + nonce);
        stranger.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        stranger.setEnabled(true);
        stranger.setLocked(false);
        stranger.setForcePasswordReset(false);
        stranger.setFailedAttempts(0);
        stranger.setCreatedAt(LocalDateTime.now());
        stranger.setUpdatedAt(LocalDateTime.now());
        stranger.setRoles(new HashSet<>(Set.of(userRole)));
        stranger = userRepository.save(stranger);
        String strangerToken = jwtService.generateAccessToken(stranger);

        ContractInstance contract = new ContractInstance();
        contract.setOrderId(testOrder.getId());
        contract.setTemplateVersionId(templateVersionId);
        contract.setUserId(testUser.getId()); // owned by testUser, not stranger
        contract.setContractStatus("SIGNED");
        contract.setRenderedContent("Stranger cannot print this");
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusDays(90));
        contract.setSignedAt(LocalDateTime.now());
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contract = contractInstanceRepository.save(contract);

        mockMvc.perform(get("/user/contracts/" + contract.getId() + "/print")
                .header("Authorization", "Bearer " + strangerToken))
               .andExpect(status().is4xxClientError());
    }

    /**
     * A reviewer (staff) can access the print page for any contract.
     */
    @Test
    void shouldAllowPrintForStaff() throws Exception {
        long nonce = System.nanoTime();
        Role reviewerRole = roleRepository.findByName("ROLE_REVIEWER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_REVIEWER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        User reviewer = new User();
        reviewer.setUsername("print_reviewer_" + nonce);
        reviewer.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        reviewer.setEnabled(true);
        reviewer.setLocked(false);
        reviewer.setForcePasswordReset(false);
        reviewer.setFailedAttempts(0);
        reviewer.setCreatedAt(LocalDateTime.now());
        reviewer.setUpdatedAt(LocalDateTime.now());
        reviewer.setRoles(new HashSet<>(Set.of(reviewerRole)));
        reviewer = userRepository.save(reviewer);
        String reviewerToken = jwtService.generateAccessToken(reviewer);

        ContractInstance contract = new ContractInstance();
        contract.setOrderId(testOrder.getId());
        contract.setTemplateVersionId(templateVersionId);
        contract.setUserId(testUser.getId()); // different user from reviewer
        contract.setContractStatus("SIGNED");
        contract.setRenderedContent("Staff can print this");
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusDays(90));
        contract.setSignedAt(LocalDateTime.now());
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contract = contractInstanceRepository.save(contract);

        mockMvc.perform(get("/user/contracts/" + contract.getId() + "/print")
                .header("Authorization", "Bearer " + reviewerToken))
               .andExpect(status().isOk())
               .andExpect(view().name("contract/print"));
    }

    // ── Dashboard exact counts ───────────────────────────────────────────────

    /**
     * Creates a deterministic set of orders for a fresh user:
     *   2 PENDING_CONFIRMATION + 3 COMPLETED + 1 CANCELED + 0 reviews
     * Then GET /user/dashboard asserts exact model attribute values.
     *
     * <p>Uses a freshly created user (not testUser from setUp) to guarantee isolation
     * from any orders already present on testUser.
     */
    @Test
    void shouldAssertExactDashboardCounts() throws Exception {
        long nonce = System.nanoTime();
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_USER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        User freshUser = new User();
        freshUser.setUsername("dash_count_" + nonce);
        freshUser.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        freshUser.setEmail("dash_count_" + nonce + "@example.com");
        freshUser.setEnabled(true);
        freshUser.setLocked(false);
        freshUser.setForcePasswordReset(false);
        freshUser.setFailedAttempts(0);
        freshUser.setCreatedAt(LocalDateTime.now());
        freshUser.setUpdatedAt(LocalDateTime.now());
        freshUser.setRoles(new HashSet<>(Set.of(userRole)));
        freshUser = userRepository.save(freshUser);
        String freshToken = jwtService.generateAccessToken(freshUser);

        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(5));
        apt.setStartTime("10:00");
        apt.setEndTime("10:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(10);
        apt.setSlotsBooked(0);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        // 2 PENDING_CONFIRMATION
        for (int i = 0; i < 2; i++) {
            Order o = new Order();
            o.setUserId(freshUser.getId());
            o.setAppointmentId(apt.getId());
            o.setOrderStatus("PENDING_CONFIRMATION");
            o.setAppointmentType("PICKUP");
            o.setRescheduleCount(0);
            o.setCurrency("USD");
            o.setTotalPrice(new BigDecimal("10.00"));
            o.setCreatedAt(LocalDateTime.now());
            o.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(o);
        }

        // 3 COMPLETED
        for (int i = 0; i < 3; i++) {
            Order o = new Order();
            o.setUserId(freshUser.getId());
            o.setAppointmentId(apt.getId());
            o.setOrderStatus("COMPLETED");
            o.setAppointmentType("PICKUP");
            o.setRescheduleCount(0);
            o.setCurrency("USD");
            o.setTotalPrice(new BigDecimal("15.00"));
            o.setCreatedAt(LocalDateTime.now());
            o.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(o);
        }

        // 1 CANCELED
        Order canceled = new Order();
        canceled.setUserId(freshUser.getId());
        canceled.setAppointmentId(apt.getId());
        canceled.setOrderStatus("CANCELED");
        canceled.setAppointmentType("PICKUP");
        canceled.setRescheduleCount(0);
        canceled.setCurrency("USD");
        canceled.setTotalPrice(new BigDecimal("5.00"));
        canceled.setCreatedAt(LocalDateTime.now());
        canceled.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(canceled);

        // No reviews for freshUser → reviewCount == 0
        MvcResult result = mockMvc.perform(get("/user/dashboard")
                .header("Authorization", "Bearer " + freshToken))
               .andExpect(status().isOk())
               .andExpect(view().name("user/dashboard"))
               .andReturn();

        var model = result.getModelAndView().getModel();

        // orderCount == 6 (2 PENDING + 3 COMPLETED + 1 CANCELED)
        Object orderCountObj = model.get("orderCount");
        assertThat(orderCountObj).isInstanceOf(Number.class);
        assertThat(((Number) orderCountObj).intValue()).isEqualTo(6);

        // pendingOrders == 2
        Object pendingObj = model.get("pendingOrders");
        assertThat(pendingObj).isInstanceOf(Number.class);
        assertThat(((Number) pendingObj).longValue()).isEqualTo(2L);

        // completedOrders == 3
        Object completedObj = model.get("completedOrders");
        assertThat(completedObj).isInstanceOf(Number.class);
        assertThat(((Number) completedObj).longValue()).isEqualTo(3L);

        // reviewCount == 0 (no reviews for this fresh user)
        Object reviewCountObj = model.get("reviewCount");
        assertThat(reviewCountObj).isInstanceOf(Number.class);
        assertThat(((Number) reviewCountObj).intValue()).isEqualTo(0);

        // recentOrders.size() <= 5 (we have 6 total, but list is limited to 5)
        Object recentObj = model.get("recentOrders");
        assertThat(recentObj).isInstanceOf(java.util.List.class);
        assertThat(((java.util.List<?>) recentObj).size()).isLessThanOrEqualTo(5);
    }

    // ── Search page tighter assertions ──────────────────────────────────────

    /**
     * After a search with a keyword that matches seeded items,
     * the model attribute "searchLogId" is a Long > 0.
     * This verifies that UserPageController.search() exposes the searchLogId from SearchResult.
     */
    @Test
    void shouldExposeSearchLogIdInModelAfterSearchRuns() throws Exception {
        long nonce = System.nanoTime();
        String keyword = "SearchLogIdTest_" + nonce;

        RecyclingItem item = new RecyclingItem();
        item.setTitle(keyword);
        item.setNormalizedTitle(keyword.toLowerCase());
        item.setDescription("Item for searchLogId test");
        item.setCategory("Electronics");
        item.setItemCondition("GOOD");
        item.setPrice(new BigDecimal("25.00"));
        item.setCurrency("USD");
        item.setSellerId(testUser.getId());
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        recyclingItemRepository.save(item);

        MvcResult result = mockMvc.perform(get("/user/search")
                .param("keyword", keyword)
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("user/search"))
               .andReturn();

        // searchLogId is set by UserPageController when hasFilters == true
        Object searchLogId = result.getModelAndView().getModel().get("searchLogId");
        assertThat(searchLogId).as("searchLogId must be populated after a keyword search").isNotNull();
        assertThat(searchLogId).isInstanceOf(Long.class);
        assertThat((Long) searchLogId).as("searchLogId must be > 0").isGreaterThan(0L);
    }

    /**
     * GET /user/search?keyword=absolutely_no_match_xyz_qqq → model "results" exists and is an empty list.
     */
    @Test
    void shouldRenderEmptyResultsSection() throws Exception {
        MvcResult result = mockMvc.perform(get("/user/search")
                .param("keyword", "absolutely_no_match_xyz_qqq_" + System.nanoTime())
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("user/search"))
               .andExpect(model().attributeExists("results"))
               .andReturn();

        Object results = result.getModelAndView().getModel().get("results");
        assertThat(results).isInstanceOf(java.util.List.class);
        assertThat(((java.util.List<?>) results)).isEmpty();
    }

    /**
     * GET /user/search with multiple filters but no keyword returns 200 and model has "results".
     * UserPageController.search() treats non-null category as hasFilters=true.
     */
    @Test
    void shouldAcceptMultipleFiltersWithoutKeyword() throws Exception {
        MvcResult result = mockMvc.perform(get("/user/search")
                .param("category", "Electronics")
                .param("minPrice", "1")
                .param("maxPrice", "100")
                .header("Authorization", "Bearer " + userToken))
               .andExpect(status().isOk())
               .andExpect(view().name("user/search"))
               .andExpect(model().attributeExists("results"))
               .andReturn();

        Object results = result.getModelAndView().getModel().get("results");
        assertThat(results).isInstanceOf(java.util.List.class);
    }
}
