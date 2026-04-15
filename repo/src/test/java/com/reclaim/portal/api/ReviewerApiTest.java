package com.reclaim.portal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateVersionRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.entity.OrderItem;
import com.reclaim.portal.orders.repository.OrderItemRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Note: RecyclingItem is from catalog package

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for reviewer-only order actions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private RecyclingItemRepository recyclingItemRepository;

    @Autowired
    private ContractTemplateRepository contractTemplateRepository;

    @Autowired
    private ContractTemplateVersionRepository contractTemplateVersionRepository;

    private User reviewer;
    private User regularUser;

    private String reviewerToken;
    private String regularUserToken;

    private Long pendingOrderId;
    private Long exceptionOrderId;

    // Extra state for new tests
    private Long orderItemId;
    private Long acceptedOrderId;
    private Long templateVersionId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role userRole = findOrCreateRole("ROLE_USER");

        reviewer = createUser("reviewer_api_" + nonce, reviewerRole);
        regularUser = createUser("regular_api_" + nonce, userRole);

        reviewerToken = jwtService.generateAccessToken(reviewer);
        regularUserToken = jwtService.generateAccessToken(regularUser);

        // Appointment for the PENDING_CONFIRMATION order — well in the future
        Appointment pendingAppt = new Appointment();
        pendingAppt.setAppointmentDate(LocalDate.now().plusDays(10));
        pendingAppt.setStartTime("09:00");
        pendingAppt.setEndTime("09:30");
        pendingAppt.setAppointmentType("PICKUP");
        pendingAppt.setSlotsAvailable(5);
        pendingAppt.setSlotsBooked(1);
        pendingAppt.setCreatedAt(LocalDateTime.now());
        pendingAppt = appointmentRepository.save(pendingAppt);

        // Order in PENDING_CONFIRMATION owned by regularUser
        Order pendingOrder = new Order();
        pendingOrder.setUserId(regularUser.getId());
        pendingOrder.setAppointmentId(pendingAppt.getId());
        pendingOrder.setOrderStatus("PENDING_CONFIRMATION");
        pendingOrder.setAppointmentType("PICKUP");
        pendingOrder.setRescheduleCount(0);
        pendingOrder.setCurrency("USD");
        pendingOrder.setTotalPrice(BigDecimal.TEN);
        pendingOrder.setCreatedAt(LocalDateTime.now());
        pendingOrder.setUpdatedAt(LocalDateTime.now());
        pendingOrder = orderRepository.save(pendingOrder);
        pendingOrderId = pendingOrder.getId();

        // Appointment for the EXCEPTION order — also well in the future
        Appointment exceptionAppt = new Appointment();
        exceptionAppt.setAppointmentDate(LocalDate.now().plusDays(10));
        exceptionAppt.setStartTime("10:00");
        exceptionAppt.setEndTime("10:30");
        exceptionAppt.setAppointmentType("PICKUP");
        exceptionAppt.setSlotsAvailable(5);
        exceptionAppt.setSlotsBooked(1);
        exceptionAppt.setCreatedAt(LocalDateTime.now());
        exceptionAppt = appointmentRepository.save(exceptionAppt);

        // Order in EXCEPTION state (cancellation flagged as too-close) owned by regularUser
        Order exceptionOrder = new Order();
        exceptionOrder.setUserId(regularUser.getId());
        exceptionOrder.setAppointmentId(exceptionAppt.getId());
        exceptionOrder.setOrderStatus("EXCEPTION");
        exceptionOrder.setAppointmentType("PICKUP");
        exceptionOrder.setRescheduleCount(0);
        exceptionOrder.setCancellationReason("Within 1 hour of appointment");
        exceptionOrder.setCurrency("USD");
        exceptionOrder.setTotalPrice(BigDecimal.TEN);
        exceptionOrder.setCreatedAt(LocalDateTime.now());
        exceptionOrder.setUpdatedAt(LocalDateTime.now());
        exceptionOrder = orderRepository.save(exceptionOrder);
        exceptionOrderId = exceptionOrder.getId();

        // Order in ACCEPTED state — used for initiateContract test
        Appointment acceptedAppt = new Appointment();
        acceptedAppt.setAppointmentDate(LocalDate.now().plusDays(15));
        acceptedAppt.setStartTime("11:00");
        acceptedAppt.setEndTime("11:30");
        acceptedAppt.setAppointmentType("PICKUP");
        acceptedAppt.setSlotsAvailable(5);
        acceptedAppt.setSlotsBooked(0);
        acceptedAppt.setCreatedAt(LocalDateTime.now());
        acceptedAppt = appointmentRepository.save(acceptedAppt);

        Order acceptedOrder = new Order();
        acceptedOrder.setUserId(regularUser.getId());
        acceptedOrder.setAppointmentId(acceptedAppt.getId());
        acceptedOrder.setOrderStatus("ACCEPTED");
        acceptedOrder.setAppointmentType("PICKUP");
        acceptedOrder.setRescheduleCount(0);
        acceptedOrder.setCurrency("USD");
        acceptedOrder.setTotalPrice(BigDecimal.TEN);
        acceptedOrder.setReviewerId(reviewer.getId());
        acceptedOrder.setCreatedAt(LocalDateTime.now());
        acceptedOrder.setUpdatedAt(LocalDateTime.now());
        acceptedOrder = orderRepository.save(acceptedOrder);
        acceptedOrderId = acceptedOrder.getId();

        // Create a real RecyclingItem to satisfy the FK on order_items.item_id
        RecyclingItem recyclingItem = new RecyclingItem();
        recyclingItem.setTitle("Reviewer Test Item " + nonce);
        recyclingItem.setNormalizedTitle("reviewer test item " + nonce);
        recyclingItem.setDescription("Item for reviewer test");
        recyclingItem.setCategory("Electronics");
        recyclingItem.setItemCondition("GOOD");
        recyclingItem.setPrice(new BigDecimal("25.00"));
        recyclingItem.setCurrency("USD");
        recyclingItem.setSellerId(regularUser.getId());
        recyclingItem.setActive(true);
        recyclingItem.setCreatedAt(LocalDateTime.now());
        recyclingItem.setUpdatedAt(LocalDateTime.now());
        recyclingItem = recyclingItemRepository.save(recyclingItem);

        // OrderItem linked to PENDING order for adjust-category test
        OrderItem item = new OrderItem();
        item.setOrderId(pendingOrderId);
        item.setItemId(recyclingItem.getId());
        item.setSnapshotTitle("Test Item");
        item.setSnapshotCategory("Electronics");
        item.setSnapshotCondition("GOOD");
        item.setSnapshotPrice(new BigDecimal("25.00"));
        item = orderItemRepository.save(item);
        orderItemId = item.getId();

        // Contract template + version for initiateContract
        ContractTemplate template = new ContractTemplate();
        template.setName("Test Template " + nonce);
        template.setDescription("desc");
        template.setActive(true);
        template.setCreatedBy(reviewer.getId());
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        template = contractTemplateRepository.save(template);

        ContractTemplateVersion version = new ContractTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNumber(1);
        version.setContent("Template content");
        version.setChangeNotes("Initial");
        version.setCreatedBy(reviewer.getId());
        version.setCreatedAt(LocalDateTime.now());
        version = contractTemplateVersionRepository.save(version);
        templateVersionId = version.getId();
    }

    // =========================================================================
    // Original tests (preserved exactly)
    // =========================================================================

    @Test
    void shouldAllowReviewerToAcceptOrder() throws Exception {
        // Reviewer accepts a PENDING_CONFIRMATION order → 200
        mockMvc.perform(put("/api/reviewer/orders/" + pendingOrderId + "/accept")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.orderStatus").value("ACCEPTED"))
               .andExpect(jsonPath("$.reviewerId").value(reviewer.getId()));
    }

    @Test
    void shouldAllowReviewerToApproveCancel() throws Exception {
        // Reviewer approves cancellation of an EXCEPTION order → 200
        Map<String, String> body = Map.of("reason", "Cancellation approved by reviewer");

        mockMvc.perform(put("/api/reviewer/orders/" + exceptionOrderId + "/approve-cancel")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.orderStatus").value("CANCELED"));
    }

    // =========================================================================
    // New tests
    // =========================================================================

    @Test
    void shouldReturnReviewerQueue() throws Exception {
        mockMvc.perform(get("/api/reviewer/queue")
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldAdjustCategoryAsReviewer() throws Exception {
        Map<String, String> body = Map.of("newCategory", "Electronics");

        MvcResult result = mockMvc.perform(put("/api/reviewer/order-items/" + orderItemId + "/adjust-category")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adjustedCategory").value("Electronics"))
                .andReturn();

        // Verify snapshot was NOT overwritten (snapshotCategory should remain "Electronics")
        var responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(responseBody.get("snapshotCategory").asText()).isEqualTo("Electronics");
    }

    @Test
    void shouldInitiateContractViaReviewerEndpoint() throws Exception {
        Map<String, Object> body = Map.of(
                "templateVersionId", templateVersionId,
                "userId", regularUser.getId(),
                "fieldValues", "{}",
                "startDate", "2099-01-01",
                "endDate", "2099-12-31"
        );

        MvcResult result = mockMvc.perform(post("/api/reviewer/orders/" + acceptedOrderId + "/initiate-contract")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractStatus").value("INITIATED"))
                .andReturn();

        // The contract's userId must be the order's owner (regularUser), NOT the reviewer
        var responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(responseBody.get("userId").asLong()).isEqualTo(regularUser.getId());
    }

    @Test
    void shouldRejectReviewerQueueForRegularUser() throws Exception {
        mockMvc.perform(get("/api/reviewer/queue")
                .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
