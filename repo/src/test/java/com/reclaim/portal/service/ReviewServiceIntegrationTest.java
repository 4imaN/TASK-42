package com.reclaim.portal.service;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.reviews.entity.ReviewImage;
import com.reclaim.portal.reviews.repository.ReviewImageRepository;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.reviews.entity.Review;
import com.reclaim.portal.reviews.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewServiceIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideStoragePath(DynamicPropertyRegistry registry) {
        registry.add("reclaim.storage.root-path", () -> tempDir.toString());
    }

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Order completedOrder;
    private Order pendingOrder;
    private Long reviewUserId;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User reviewUser = new User();
        reviewUser.setUsername("review_user_" + System.nanoTime());
        reviewUser.setPasswordHash(passwordEncoder.encode("Pass1!wordXXX"));
        reviewUser.setEnabled(true);
        reviewUser.setLocked(false);
        reviewUser.setForcePasswordReset(false);
        reviewUser.setFailedAttempts(0);
        reviewUser.setCreatedAt(LocalDateTime.now());
        reviewUser.setUpdatedAt(LocalDateTime.now());
        reviewUser.setRoles(new HashSet<>(Set.of(role)));
        reviewUser = userRepository.save(reviewUser);
        reviewUserId = reviewUser.getId();

        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(LocalDate.now().plusDays(3));
        appointment.setStartTime("10:00");
        appointment.setEndTime("10:30");
        appointment.setAppointmentType("PICKUP");
        appointment.setSlotsAvailable(5);
        appointment.setSlotsBooked(1);
        appointment.setCreatedAt(LocalDateTime.now());
        appointment = appointmentRepository.save(appointment);

        completedOrder = new Order();
        completedOrder.setUserId(reviewUserId);
        completedOrder.setAppointmentId(appointment.getId());
        completedOrder.setOrderStatus("COMPLETED");
        completedOrder.setAppointmentType("PICKUP");
        completedOrder.setRescheduleCount(0);
        completedOrder.setCurrency("USD");
        completedOrder.setTotalPrice(new BigDecimal("10.00"));
        completedOrder.setCreatedAt(LocalDateTime.now());
        completedOrder.setUpdatedAt(LocalDateTime.now());
        completedOrder = orderRepository.save(completedOrder);

        pendingOrder = new Order();
        pendingOrder.setUserId(reviewUserId);
        pendingOrder.setAppointmentId(appointment.getId());
        pendingOrder.setOrderStatus("PENDING_CONFIRMATION");
        pendingOrder.setAppointmentType("PICKUP");
        pendingOrder.setRescheduleCount(0);
        pendingOrder.setCurrency("USD");
        pendingOrder.setTotalPrice(new BigDecimal("5.00"));
        pendingOrder.setCreatedAt(LocalDateTime.now());
        pendingOrder.setUpdatedAt(LocalDateTime.now());
        pendingOrder = orderRepository.save(pendingOrder);
    }

    @Test
    void shouldCreateReview() {
        Review review = reviewService.createReview(completedOrder.getId(), reviewUserId, 5, "Excellent service!");

        assertThat(review).isNotNull();
        assertThat(review.getId()).isNotNull();
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getReviewText()).isEqualTo("Excellent service!");
        assertThat(review.getOrderId()).isEqualTo(completedOrder.getId());
    }

    @Test
    void shouldRejectForNonCompleted() {
        assertThatThrownBy(() ->
            reviewService.createReview(pendingOrder.getId(), reviewUserId, 4, "Good")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("completed");
    }

    @Test
    void shouldRejectInvalidRatingTooLow() {
        assertThatThrownBy(() ->
            reviewService.createReview(completedOrder.getId(), reviewUserId, 0, "Bad rating")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("between 1 and 5");
    }

    @Test
    void shouldRejectInvalidRatingTooHigh() {
        assertThatThrownBy(() ->
            reviewService.createReview(completedOrder.getId(), reviewUserId, 6, "Too high")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("between 1 and 5");
    }

    @Test
    void shouldRejectLongText() {
        String longText = "A".repeat(1001);

        assertThatThrownBy(() ->
            reviewService.createReview(completedOrder.getId(), reviewUserId, 3, longText)
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("1000");
    }

    @Test
    void shouldRejectDuplicateReview() {
        reviewService.createReview(completedOrder.getId(), reviewUserId, 5, "First review");

        assertThatThrownBy(() ->
            reviewService.createReview(completedOrder.getId(), reviewUserId, 3, "Second review")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("already exists");
    }

    @Test
    void shouldGetReviewsByUser() {
        reviewService.createReview(completedOrder.getId(), reviewUserId, 5, "Great!");

        var reviews = reviewService.getReviewsByUser(reviewUserId);
        assertThat(reviews).isNotEmpty();
        assertThat(reviews).allMatch(r -> r.getReviewerUserId().equals(reviewUserId));
    }

    // Minimal 1x1 PNG bytes
    private static final byte[] MINIMAL_PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
        (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
        0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xFF, (byte) 0xFF, 0x3F,
        0x00, 0x05, (byte) 0xFE, 0x02, (byte) 0xFE, (byte) 0xDC, (byte) 0xCC, 0x59,
        (byte) 0xE7, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
        0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @Test
    void shouldAddReviewImage() {
        // Create a review first
        var review = reviewService.createReview(completedOrder.getId(), reviewUserId, 4, "Good service");

        MockMultipartFile imageFile = new MockMultipartFile(
            "file", "review-photo.png", "image/png", MINIMAL_PNG);

        ReviewImage image = reviewService.addReviewImage(review.getId(), reviewUserId, imageFile);

        assertThat(image).isNotNull();
        assertThat(image.getId()).isNotNull();
        assertThat(image.getReviewId()).isEqualTo(review.getId());
        assertThat(image.getFilePath()).isNotBlank();
        assertThat(image.getChecksum()).isNotBlank();
        assertThat(image.getDisplayOrder()).isEqualTo(0);
    }

    @Test
    void shouldAddMultipleImagesToReview() {
        var review = reviewService.createReview(completedOrder.getId(), reviewUserId, 3, "Okay");

        for (int i = 1; i <= 3; i++) {
            MockMultipartFile imageFile = new MockMultipartFile(
                "file", "photo" + i + ".png", "image/png", MINIMAL_PNG);
            reviewService.addReviewImage(review.getId(), reviewUserId, imageFile);
        }

        long count = reviewImageRepository.countByReviewId(review.getId());
        assertThat(count).isEqualTo(3);
    }

    @Test
    void shouldRejectAddImageByWrongUser() {
        var review = reviewService.createReview(completedOrder.getId(), reviewUserId, 5, "Test");

        MockMultipartFile imageFile = new MockMultipartFile(
            "file", "bad.png", "image/png", MINIMAL_PNG);

        Long differentUserId = reviewUserId + 9999L;
        assertThatThrownBy(() -> reviewService.addReviewImage(review.getId(), differentUserId, imageFile))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("own reviews");
    }

    @Test
    void shouldRejectImageWhenAtMaxLimit() {
        var review = reviewService.createReview(completedOrder.getId(), reviewUserId, 5, "Full review");

        // Add 5 images (the maximum)
        for (int i = 0; i < 5; i++) {
            MockMultipartFile imageFile = new MockMultipartFile(
                "file", "img" + i + ".png", "image/png", MINIMAL_PNG);
            reviewService.addReviewImage(review.getId(), reviewUserId, imageFile);
        }

        // 6th image should be rejected
        MockMultipartFile extraFile = new MockMultipartFile(
            "file", "extra.png", "image/png", MINIMAL_PNG);
        assertThatThrownBy(() -> reviewService.addReviewImage(review.getId(), reviewUserId, extraFile))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("5 images");
    }

    @Test
    void shouldGetReviewForOrder() {
        reviewService.createReview(completedOrder.getId(), reviewUserId, 5, "Excellent!");

        Map<String, Object> result = reviewService.getReviewForOrder(completedOrder.getId());

        assertThat(result).containsKey("review");
        assertThat(result).containsKey("images");

        var review = (com.reclaim.portal.reviews.entity.Review) result.get("review");
        assertThat(review.getOrderId()).isEqualTo(completedOrder.getId());
        assertThat(review.getRating()).isEqualTo(5);

        @SuppressWarnings("unchecked")
        var images = (List<ReviewImage>) result.get("images");
        assertThat(images).isEmpty(); // no images added
    }

    @Test
    void shouldGetReviewForOrderWithImages() {
        var review = reviewService.createReview(completedOrder.getId(), reviewUserId, 4, "Good");
        MockMultipartFile imageFile = new MockMultipartFile(
            "file", "snap.png", "image/png", MINIMAL_PNG);
        reviewService.addReviewImage(review.getId(), reviewUserId, imageFile);

        Map<String, Object> result = reviewService.getReviewForOrder(completedOrder.getId());

        @SuppressWarnings("unchecked")
        var images = (List<ReviewImage>) result.get("images");
        assertThat(images).hasSize(1);
        assertThat(images.get(0).getReviewId()).isEqualTo(review.getId());
    }

    @Test
    void shouldThrowForGetReviewForOrderWithNoReview() {
        assertThatThrownBy(() -> reviewService.getReviewForOrder(pendingOrder.getId()))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void shouldRejectAddImageToNonExistentReview() {
        MockMultipartFile imageFile = new MockMultipartFile(
            "file", "ghost.png", "image/png", MINIMAL_PNG);

        assertThatThrownBy(() -> reviewService.addReviewImage(999999L, reviewUserId, imageFile))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
