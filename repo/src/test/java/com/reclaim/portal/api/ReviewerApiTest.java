package com.reclaim.portal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
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
import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private User reviewer;
    private User regularUser;

    private String reviewerToken;

    private Long pendingOrderId;
    private Long exceptionOrderId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role userRole = findOrCreateRole("ROLE_USER");

        reviewer = createUser("reviewer_api_" + nonce, reviewerRole);
        regularUser = createUser("regular_api_" + nonce, userRole);

        reviewerToken = jwtService.generateAccessToken(reviewer);

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
    }

    @Test
    void shouldAllowReviewerToAcceptOrder() throws Exception {
        // Reviewer accepts a PENDING_CONFIRMATION order → 200
        mockMvc.perform(put("/api/reviewer/orders/" + pendingOrderId + "/accept")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken))
               .andExpect(status().isOk());
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
               .andExpect(status().isOk());
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
