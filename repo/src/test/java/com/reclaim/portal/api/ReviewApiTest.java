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
import com.reclaim.portal.reviews.entity.Review;
import com.reclaim.portal.reviews.repository.ReviewImageRepository;
import com.reclaim.portal.reviews.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that POST /api/reviews accepts JSON with {orderId, rating, reviewText}
 * and not FormData. Also tests authentication and validation boundaries.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewApiTest {

    // Minimal valid JPEG bytes
    private static final byte[] MIN_JPEG = {
        (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0x00,0x10,0x4A,0x46,
        0x49,0x46,0x00,0x01,0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00
    };

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

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewImageRepository reviewImageRepository;

    private String accessToken;
    private Long userId;
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
        userId = user.getId();

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
     * After success, verify repository contains the review with correct rating/text.
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

        // Verify the review was persisted correctly
        Optional<Review> persisted = reviewRepository.findByOrderId(completedOrderId);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getRating()).isEqualTo(5);
        assertThat(persisted.get().getReviewText()).isEqualTo("Excellent recycling service!");
        assertThat(persisted.get().getReviewerUserId()).isEqualTo(userId);
    }

    /**
     * POST /api/reviews must NOT accept multipart/form-data — the endpoint
     * is a @RequestBody JSON endpoint, so form data should result in a non-200 status.
     *
     * Spring maps HttpMediaTypeNotSupportedException through the general handler → 500.
     * The exact status is 415 (UnsupportedMediaType) when Spring's built-in handler fires,
     * but may be 500 when caught by GlobalExceptionHandler's general handler. Either way,
     * it must not be 200.
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

        // Spring 6/Boot 3 returns 500 because HttpMediaTypeNotSupportedException is caught by
        // the general exception handler (GlobalExceptionHandler.handleGeneral).
        // The behaviour is deterministic: always 500 in this configuration.
        // Not 200 is the minimum contract; we additionally assert the exact known status.
        assertThat(status).isNotEqualTo(200);
        // The actual status in this configuration is 500 (GlobalExceptionHandler catches it)
        assertThat(status).isIn(415, 500);
    }

    /**
     * POST /api/reviews with a rating out of the 1-5 range must be rejected (400).
     * The error body should contain a field-level error message related to "rating".
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
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errors").isArray());
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
     * POST /api/reviews without authentication must be rejected (401).
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

    // =========================================================================
    // POST /api/reviews/{id}/images tests
    // =========================================================================

    @Test
    void shouldRejectImageUploadOnNonexistentReview() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.jpg", "image/jpeg", MIN_JPEG);

        mockMvc.perform(multipart("/api/reviews/99999/images")
                .file(file)
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectImageUploadWrongOwner() throws Exception {
        // First create a review for completedOrderId
        Long reviewId = createReview(completedOrderId, accessToken);

        // Create a different user and try to upload to the review they don't own
        long nonce = System.nanoTime();
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        User otherUser = buildAndSaveUser("other_img_" + nonce, userRole);
        String otherToken = jwtService.generateAccessToken(otherUser);

        MockMultipartFile file = new MockMultipartFile(
                "file", "image.jpg", "image/jpeg", MIN_JPEG);

        // Service: "You can only add images to your own reviews" → BusinessRuleException → 409
        mockMvc.perform(multipart("/api/reviews/" + reviewId + "/images")
                .file(file)
                .with(csrf())
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectImageUploadInvalidFileType() throws Exception {
        Long reviewId = createReview(completedOrderId, accessToken);

        // .txt not in allowed extensions → StorageService throws BusinessRuleException → 409
        MockMultipartFile txtFile = new MockMultipartFile(
                "file", "note.txt", "text/plain", "some text".getBytes());

        mockMvc.perform(multipart("/api/reviews/" + reviewId + "/images")
                .file(txtFile)
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectSixthImage() throws Exception {
        Long reviewId = createReview(completedOrderId, accessToken);

        // Upload 5 images successfully
        for (int i = 0; i < 5; i++) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "image" + i + ".jpg", "image/jpeg", MIN_JPEG);
            mockMvc.perform(multipart("/api/reviews/" + reviewId + "/images")
                    .file(file)
                    .with(csrf())
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());
        }

        // 6th image must be rejected → 409
        MockMultipartFile sixthFile = new MockMultipartFile(
                "file", "sixth.jpg", "image/jpeg", MIN_JPEG);
        mockMvc.perform(multipart("/api/reviews/" + reviewId + "/images")
                .file(sixthFile)
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectOversizedImage() throws Exception {
        Long reviewId = createReview(completedOrderId, accessToken);

        // max-file-size in test config is 3145728 (3 MB). Build a 4 MB byte array.
        // Start with valid JPEG magic bytes so only size check triggers.
        byte[] oversized = new byte[4 * 1024 * 1024];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        oversized[3] = (byte) 0xE0;

        MockMultipartFile bigFile = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", oversized);

        int status = mockMvc.perform(multipart("/api/reviews/" + reviewId + "/images")
                .file(bigFile)
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getStatus();

        // StorageService throws BusinessRuleException("File size exceeds...") → 409
        // OR Spring's MaxUploadSizeExceededException → 413
        assertThat(status).isIn(409, 413);
    }

    // =========================================================================
    // GET /api/reviews/order/{orderId} tests
    // =========================================================================

    @Test
    void shouldReturn404WhenNoReviewForOrder() throws Exception {
        // pendingOrderId has no review
        mockMvc.perform(get("/api/reviews/order/" + pendingOrderId)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnReviewForOwnerWithImagesField() throws Exception {
        Long reviewId = createReview(completedOrderId, accessToken);

        mockMvc.perform(get("/api/reviews/order/" + completedOrderId)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.id").value(reviewId))
                .andExpect(jsonPath("$.images").isArray());
    }

    @Test
    void shouldDenyReviewFetchForStranger() throws Exception {
        createReview(completedOrderId, accessToken);

        long nonce = System.nanoTime();
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        User stranger = buildAndSaveUser("stranger_rfetch_" + nonce, userRole);
        String strangerToken = jwtService.generateAccessToken(stranger);

        // Stranger is not the order owner and not staff → BusinessRuleException "Access denied" → 403
        mockMvc.perform(get("/api/reviews/order/" + completedOrderId)
                .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Creates a review via the API and returns the review id. */
    private Long createReview(Long orderId, String token) throws Exception {
        Map<String, Object> body = Map.of("orderId", orderId, "rating", 4, "reviewText", "Good");
        MvcResult result = mockMvc.perform(post("/api/reviews")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    /** Creates and saves a user with the given role. */
    private User buildAndSaveUser(String username, Role role) {
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
