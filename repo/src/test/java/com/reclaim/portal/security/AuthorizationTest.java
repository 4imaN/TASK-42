package com.reclaim.portal.security;

import com.reclaim.portal.appeals.entity.Appeal;
import com.reclaim.portal.appeals.repository.AppealRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for cross-user access denial and role-based authorization.
 *
 * BusinessRuleException maps to HTTP 409 (Conflict) via GlobalExceptionHandler.
 * @PreAuthorize failures map to HTTP 403 (Forbidden).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ContractTemplateRepository templateRepository;

    @Autowired
    private ContractTemplateVersionRepository templateVersionRepository;

    @Autowired
    private ContractInstanceRepository contractInstanceRepository;

    @Autowired
    private AppealRepository appealRepository;

    private User userA;
    private User userB;
    private User reviewer;
    private User admin;

    private String userAToken;
    private String userBToken;
    private String reviewerToken;

    private Long orderId;
    private Long contractId;
    private Long appealId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        // Resolve or create roles
        Role userRole = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role adminRole = findOrCreateRole("ROLE_ADMIN");

        // Create userA with ROLE_USER
        userA = createUser("authz_userA_" + nonce, userRole);

        // Create userB with ROLE_USER
        userB = createUser("authz_userB_" + nonce, userRole);

        // Create reviewer with ROLE_REVIEWER
        reviewer = createUser("authz_reviewer_" + nonce, reviewerRole);

        // Create admin with ROLE_ADMIN
        admin = createUser("authz_admin_" + nonce, adminRole);

        // Generate JWT tokens
        userAToken = jwtService.generateAccessToken(userA);
        userBToken = jwtService.generateAccessToken(userB);
        reviewerToken = jwtService.generateAccessToken(reviewer);

        // Create an appointment directly via repository (bypassing time validation)
        // Use a future date to be safe — set 5 days out, far future time
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(LocalDate.now().plusDays(5));
        appointment.setStartTime("10:00");
        appointment.setEndTime("10:30");
        appointment.setAppointmentType("PICKUP");
        appointment.setSlotsAvailable(5);
        appointment.setSlotsBooked(1); // mark one slot booked for the order we'll create
        appointment.setCreatedAt(LocalDateTime.now());
        appointment = appointmentRepository.save(appointment);

        // Create an order owned by userA directly via repository (bypassing OrderService time checks)
        Order order = new Order();
        order.setUserId(userA.getId());
        order.setAppointmentId(appointment.getId());
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(BigDecimal.TEN);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        orderId = order.getId();

        // Create a contract template + version (needed for ContractInstance FK)
        ContractTemplate template = new ContractTemplate();
        template.setName("Test Template " + nonce);
        template.setDescription("Test");
        template.setActive(true);
        template.setCreatedBy(admin.getId());
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        template = templateRepository.save(template);

        ContractTemplateVersion version = new ContractTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNumber(1);
        version.setContent("Test contract content");
        version.setChangeNotes("Initial version");
        version.setCreatedBy(admin.getId());
        version.setCreatedAt(LocalDateTime.now());
        version = templateVersionRepository.save(version);

        // Create a contract instance owned by userA directly via repository
        ContractInstance contract = new ContractInstance();
        contract.setOrderId(orderId);
        contract.setTemplateVersionId(version.getId());
        contract.setUserId(userA.getId());
        contract.setReviewerId(null);
        contract.setContractStatus("INITIATED");
        contract.setRenderedContent("Test rendered content");
        contract.setFieldValues("{}");
        contract.setStartDate(LocalDate.now().plusDays(1));
        contract.setEndDate(LocalDate.now().plusDays(365));
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contract = contractInstanceRepository.save(contract);
        contractId = contract.getId();

        // Create an appeal by userA directly via repository
        Appeal appeal = new Appeal();
        appeal.setOrderId(orderId);
        appeal.setContractId(contractId);
        appeal.setAppellantId(userA.getId());
        appeal.setReason("Test appeal reason");
        appeal.setAppealStatus("OPEN");
        appeal.setCreatedAt(LocalDateTime.now());
        appeal.setUpdatedAt(LocalDateTime.now());
        appeal = appealRepository.save(appeal);
        appealId = appeal.getId();
    }

    // =========================================================================
    // User profile access tests
    // =========================================================================

    @Test
    void shouldDenyRegularUserAccessToOtherUserProfile() throws Exception {
        // userB tries to access userA's profile → should get 409 (BusinessRuleException)
        mockMvc.perform(get("/api/users/" + userA.getId() + "/profile")
                .header("Authorization", "Bearer " + userBToken))
               .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Order access tests
    // =========================================================================

    @Test
    void shouldDenyUserBAccessToUserAOrder() throws Exception {
        // userB does not own the order → "Access denied" → 403
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + userBToken))
               .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowUserAAccessToOwnOrder() throws Exception {
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + userAToken))
               .andExpect(status().isOk());
    }

    @Test
    void shouldAllowReviewerAccessToOrder() throws Exception {
        // Reviewer is staff → isStaff=true → no ownership check
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + reviewerToken))
               .andExpect(status().isOk());
    }

    @Test
    void shouldDenyUserBCancelOfUserAOrder() throws Exception {
        // userB tries to cancel userA's order → "Access denied" → 403
        mockMvc.perform(put("/api/orders/" + orderId + "/cancel")
                .with(csrf())
                .header("Authorization", "Bearer " + userBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"test cancel\"}"))
               .andExpect(status().isForbidden());
    }

    @Test
    void shouldDenyRegularUserAcceptOrder() throws Exception {
        // acceptOrder has @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
        // userA has ROLE_USER only → 403 Forbidden
        mockMvc.perform(put("/api/orders/" + orderId + "/accept")
                .with(csrf())
                .header("Authorization", "Bearer " + userAToken))
               .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowReviewerAcceptOrder() throws Exception {
        // Reviewer has ROLE_REVIEWER → @PreAuthorize passes, order is in PENDING_CONFIRMATION → 200
        mockMvc.perform(put("/api/orders/" + orderId + "/accept")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken))
               .andExpect(status().isOk());
    }

    // =========================================================================
    // Contract access tests
    // =========================================================================

    @Test
    void shouldDenyUserBAccessToUserAContract() throws Exception {
        // userB does not own the contract → "Access denied to contract" → 403
        mockMvc.perform(get("/api/contracts/" + contractId)
                .header("Authorization", "Bearer " + userBToken))
               .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowUserAAccessToOwnContract() throws Exception {
        mockMvc.perform(get("/api/contracts/" + contractId)
                .header("Authorization", "Bearer " + userAToken))
               .andExpect(status().isOk());
    }

    // =========================================================================
    // Appeal access tests
    // =========================================================================

    @Test
    void shouldDenyUserBAccessToUserAAppeal() throws Exception {
        // userB is not the appellant → "Access denied to appeal" → 403
        mockMvc.perform(get("/api/appeals/" + appealId)
                .header("Authorization", "Bearer " + userBToken))
               .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowUserAAccessToOwnAppeal() throws Exception {
        mockMvc.perform(get("/api/appeals/" + appealId)
                .header("Authorization", "Bearer " + userAToken))
               .andExpect(status().isOk());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Role findOrCreateRole(String roleName) {
        return roleRepository.findByName(roleName).orElseGet(() -> {
            Role r = new Role();
            r.setName(roleName);
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
