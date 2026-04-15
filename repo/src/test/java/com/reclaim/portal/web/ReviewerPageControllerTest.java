package com.reclaim.portal.web;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.contracts.entity.ContractTemplate;
import com.reclaim.portal.contracts.entity.ContractTemplateVersion;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ReviewerPageController — verifies route resolution, authorization,
 * and model population for /reviewer/orders/{id}.
 *
 * <p>Mirrors the setup pattern from UserPageControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewerPageControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ContractTemplateRepository contractTemplateRepository;
    @Autowired private ContractTemplateVersionRepository contractTemplateVersionRepository;

    private User reviewerUser;
    private User adminUser;
    private User regularUser;
    private User orderOwner;

    private String reviewerToken;
    private String adminToken;
    private String userToken;

    private Order pendingOrder;
    private ContractTemplate template;
    private ContractTemplateVersion templateVersion;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role adminRole    = findOrCreateRole("ROLE_ADMIN");
        Role userRole     = findOrCreateRole("ROLE_USER");

        reviewerUser = createUser("rp_reviewer_" + nonce, reviewerRole);
        adminUser    = createUser("rp_admin_"    + nonce, adminRole);
        regularUser  = createUser("rp_user_"     + nonce, userRole);
        orderOwner   = createUser("rp_owner_"    + nonce, userRole);

        reviewerToken = jwtService.generateAccessToken(reviewerUser);
        adminToken    = jwtService.generateAccessToken(adminUser);
        userToken     = jwtService.generateAccessToken(regularUser);

        // Appointment for the order
        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(3));
        apt.setStartTime("09:00");
        apt.setEndTime("09:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(10);
        apt.setSlotsBooked(0);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        // PENDING_CONFIRMATION order owned by orderOwner (a different user)
        pendingOrder = new Order();
        pendingOrder.setUserId(orderOwner.getId());
        pendingOrder.setAppointmentId(apt.getId());
        pendingOrder.setOrderStatus("PENDING_CONFIRMATION");
        pendingOrder.setAppointmentType("PICKUP");
        pendingOrder.setRescheduleCount(0);
        pendingOrder.setCurrency("USD");
        pendingOrder.setTotalPrice(new BigDecimal("50.00"));
        pendingOrder.setCreatedAt(LocalDateTime.now());
        pendingOrder.setUpdatedAt(LocalDateTime.now());
        pendingOrder = orderRepository.save(pendingOrder);

        // Contract template + version so templateVersionOptions is populated
        template = new ContractTemplate();
        template.setName("Reviewer Test Template " + nonce);
        template.setDescription("Template for reviewer test");
        template.setActive(true);
        template.setCreatedBy(adminUser.getId());
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        template = contractTemplateRepository.save(template);

        templateVersion = new ContractTemplateVersion();
        templateVersion.setTemplateId(template.getId());
        templateVersion.setVersionNumber(1);
        templateVersion.setContent("Contract template content v1");
        templateVersion.setChangeNotes("Initial version");
        templateVersion.setCreatedBy(adminUser.getId());
        templateVersion.setCreatedAt(LocalDateTime.now());
        templateVersion = contractTemplateVersionRepository.save(templateVersion);
    }

    // ── Authorization ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "reviewer", roles = {"REVIEWER"})
    void shouldReturnReviewerOrderDetailForReviewer() throws Exception {
        mockMvc.perform(get("/reviewer/orders/" + pendingOrder.getId()))
               .andExpect(status().isOk())
               .andExpect(view().name("reviewer/order-detail"))
               .andExpect(model().attributeExists("order", "items", "categories", "templateVersionOptions"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void shouldReturnReviewerOrderDetailForAdmin() throws Exception {
        mockMvc.perform(get("/reviewer/orders/" + pendingOrder.getId()))
               .andExpect(status().isOk())
               .andExpect(view().name("reviewer/order-detail"))
               .andExpect(model().attributeExists("order", "items", "categories", "templateVersionOptions"));
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void shouldDenyReviewerOrderDetailForRegularUser() throws Exception {
        // @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')") on the controller class blocks USER
        mockMvc.perform(get("/reviewer/orders/" + pendingOrder.getId()))
               .andExpect(status().isForbidden());
    }

    @Test
    void shouldRedirectUnauthenticatedToLogin() throws Exception {
        // No authentication — Spring Security should return 401 or 3xx redirect
        int statusCode = mockMvc.perform(get("/reviewer/orders/" + pendingOrder.getId()))
               .andReturn().getResponse().getStatus();

        // The AuthenticationEntryPoint may return 401 (for API) or 3xx (for SSR redirect)
        assertThat(statusCode).as("unauthenticated request should be denied (401 or 3xx)")
               .satisfiesAnyOf(
                   s -> assertThat(s).isEqualTo(401),
                   s -> assertThat(s).isBetween(300, 399)
               );
    }

    // ── 404 for missing order ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "reviewer", roles = {"REVIEWER"})
    void shouldReturn404ForMissingReviewerOrder() throws Exception {
        mockMvc.perform(get("/reviewer/orders/99999"))
               .andExpect(status().isNotFound());
    }

    // ── templateVersionOptions model attribute ────────────────────────────────

    /**
     * After seeding two versions for the same template, templateVersionOptions should be a
     * non-empty list whose first element's "label" contains the template name.
     */
    @Test
    @WithMockUser(username = "reviewer", roles = {"REVIEWER"})
    void shouldPopulateTemplateVersionOptionsWithLatestVersion() throws Exception {
        // Seed a second version so we have v1 and v2; controller returns the latest (highest versionNumber)
        ContractTemplateVersion v2 = new ContractTemplateVersion();
        v2.setTemplateId(template.getId());
        v2.setVersionNumber(2);
        v2.setContent("Contract template content v2");
        v2.setChangeNotes("Second version");
        v2.setCreatedBy(adminUser.getId());
        v2.setCreatedAt(LocalDateTime.now());
        contractTemplateVersionRepository.save(v2);

        MvcResult result = mockMvc.perform(get("/reviewer/orders/" + pendingOrder.getId()))
               .andExpect(status().isOk())
               .andExpect(model().attributeExists("templateVersionOptions"))
               .andReturn();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options =
                (List<Map<String, Object>>) result.getModelAndView().getModel().get("templateVersionOptions");

        assertThat(options).as("templateVersionOptions must be a list").isNotNull();
        assertThat(options).as("templateVersionOptions must be non-empty (template seeded in @BeforeEach)").isNotEmpty();

        // Locate the option for *this test's* template by name — prior HTTP-level tests
        // in the same JVM run commit additional active templates with lower IDs that
        // appear earlier in the controller's list.
        Map<String, Object> ourOption = options.stream()
                .filter(o -> {
                    Object lbl = o.get("label");
                    return lbl instanceof String && ((String) lbl).contains(template.getName());
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No templateVersionOption found with label containing '" + template.getName() + "'"));

        String label = (String) ourOption.get("label");
        assertThat(label).as("label must contain template name").contains(template.getName());
        // Controller returns the latest (highest version number) — we seeded v1 and v2 above.
        assertThat(label).as("label must reflect the latest seeded version").contains("v2");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
