package com.reclaim.portal.web;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.contracts.entity.ContractInstance;
import com.reclaim.portal.contracts.entity.ContractTemplate;
import com.reclaim.portal.contracts.entity.ContractTemplateVersion;
import com.reclaim.portal.contracts.repository.ContractInstanceRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateVersionRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
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
}
