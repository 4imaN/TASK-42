package com.reclaim.portal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that POST /api/reviews accepts JSON with {orderId, rating, reviewText}
 * and not FormData. Also tests authentication and validation boundaries.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewApiTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    private String accessToken;
    private Long completedOrderId;
    private Long pendingOrderId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User user = new User();
        user.setUsername("reviewapi_user_" + nonce);
        user.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        user.setEmail("reviewapi_" + nonce + "@example.com");
        user.setEnabled(true);
        user.setLocked(false);
        user.setForcePasswordReset(false);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>(Set.of(userRole)));
        user = userRepository.save(user);

        accessToken = jwtService.generateAccessToken(user);

        // Create a real appointment to satisfy the FK constraint on orders.appointment_id
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(LocalDate.now().plusDays(5));
        appointment.setStartTime("14:00");
        appointment.setEndTime("14:30");
        appointment.setAppointmentType("PICKUP");
        appointment.setSlotsAvailable(5);
        appointment.setSlotsBooked(2);
        appointment.setCreatedAt(LocalDateTime.now());
        appointment = appointmentRepository.save(appointment);
        Long apptId = appointment.getId();

        // Create a COMPLETED order — only COMPLETED orders can be reviewed
        Order completedOrder = new Order();
        completedOrder.setUserId(user.getId());
        completedOrder.setAppointmentId(apptId);
        completedOrder.setOrderStatus("COMPLETED");
        completedOrder.setAppointmentType("PICKUP");
        completedOrder.setRescheduleCount(0);
        completedOrder.setCurrency("USD");
        completedOrder.setTotalPrice(BigDecimal.TEN);
        completedOrder.setCreatedAt(LocalDateTime.now());
        completedOrder.setUpdatedAt(LocalDateTime.now());
        completedOrder = orderRepository.save(completedOrder);
        completedOrderId = completedOrder.getId();

        // Create a PENDING order — reviews for non-COMPLETED orders must be rejected
        Order pendingOrder = new Order();
        pendingOrder.setUserId(user.getId());
        pendingOrder.setAppointmentId(apptId);
        pendingOrder.setOrderStatus("PENDING_CONFIRMATION");
        pendingOrder.setAppointmentType("PICKUP");
        pendingOrder.setRescheduleCount(0);
        pendingOrder.setCurrency("USD");
        pendingOrder.setTotalPrice(BigDecimal.TEN);
        pendingOrder.setCreatedAt(LocalDateTime.now());
        pendingOrder.setUpdatedAt(LocalDateTime.now());
        pendingOrder = orderRepository.save(pendingOrder);
        pendingOrderId = pendingOrder.getId();
    }

    /**
     * POST /api/reviews must accept JSON body with {orderId, rating, reviewText}.
     * A 200 response confirms the JSON wire shape is correct and the endpoint is wired properly.
     */
    @Test
    void createReviewAcceptsJsonWithOrderIdRatingAndReviewText() throws Exception {
        Map<String, Object> requestBody = Map.of(
            "orderId", completedOrderId,
            "rating", 5,
            "reviewText", "Excellent recycling service!"
        );

        mockMvc.perform(post("/api/reviews")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
               .andExpect(status().isOk());
    }

    /**
     * POST /api/reviews must NOT accept multipart/form-data — the endpoint
     * is a @RequestBody JSON endpoint, so form data should result in a non-200 status.
     */
    @Test
    void createReviewRejectsFormData() throws Exception {
        int status = mockMvc.perform(post("/api/reviews")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .param("orderId", completedOrderId.toString())
                .param("rating", "5")
                .param("reviewText", "Some review"))
               .andReturn().getResponse().getStatus();

        // Form data is not supported for @RequestBody JSON endpoints
        assertThat(status).isNotEqualTo(200);
    }

    /**
     * POST /api/reviews with a rating out of the 1-5 range must be rejected (409).
     */
    @Test
    void createReviewRejectsInvalidRating() throws Exception {
        Map<String, Object> requestBody = Map.of(
            "orderId", completedOrderId,
            "rating", 6,
            "reviewText", "Rating out of range"
        );

        mockMvc.perform(post("/api/reviews")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
               .andExpect(status().isBadRequest());
    }

    /**
     * POST /api/reviews for a non-COMPLETED order must be rejected with a business rule error (409).
     */
    @Test
    void createReviewRejectsNonCompletedOrder() throws Exception {
        Map<String, Object> requestBody = Map.of(
            "orderId", pendingOrderId,
            "rating", 4,
            "reviewText", "Good service"
        );

        mockMvc.perform(post("/api/reviews")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
               .andExpect(status().isConflict());
    }

    /**
     * POST /api/reviews without authentication must be rejected (403).
     */
    @Test
    void createReviewRequiresAuthentication() throws Exception {
        Map<String, Object> requestBody = Map.of(
            "orderId", completedOrderId,
            "rating", 5,
            "reviewText", "Great!"
        );

        mockMvc.perform(post("/api/reviews")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
               .andExpect(status().isUnauthorized());
    }
}
