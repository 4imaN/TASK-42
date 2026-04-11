package com.reclaim.portal.service;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.exception.BusinessRuleException;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that review creation enforces order ownership:
 * - A non-owner cannot create a review for another user's order.
 * - The order owner can create a review for a completed order.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewOwnershipTest {

    @Autowired
    private ReviewService reviewService;

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

    private Long userAId;
    private Long userBId;
    private Long completedOrderId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        // Create userA — the order owner
        User userA = new User();
        userA.setUsername("ownership_userA_" + nonce);
        userA.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        userA.setEmail("ownership_a_" + nonce + "@example.com");
        userA.setEnabled(true);
        userA.setLocked(false);
        userA.setForcePasswordReset(false);
        userA.setFailedAttempts(0);
        userA.setCreatedAt(LocalDateTime.now());
        userA.setUpdatedAt(LocalDateTime.now());
        userA.setRoles(new HashSet<>(Set.of(userRole)));
        userA = userRepository.save(userA);
        userAId = userA.getId();

        // Create userB — a different user who does not own the order
        User userB = new User();
        userB.setUsername("ownership_userB_" + nonce);
        userB.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        userB.setEmail("ownership_b_" + nonce + "@example.com");
        userB.setEnabled(true);
        userB.setLocked(false);
        userB.setForcePasswordReset(false);
        userB.setFailedAttempts(0);
        userB.setCreatedAt(LocalDateTime.now());
        userB.setUpdatedAt(LocalDateTime.now());
        userB.setRoles(new HashSet<>(Set.of(userRole)));
        userB = userRepository.save(userB);
        userBId = userB.getId();

        // Create an appointment (required by the order FK)
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(LocalDate.now().plusDays(3));
        appointment.setStartTime("10:00");
        appointment.setEndTime("10:30");
        appointment.setAppointmentType("PICKUP");
        appointment.setSlotsAvailable(5);
        appointment.setSlotsBooked(1);
        appointment.setCreatedAt(LocalDateTime.now());
        appointment = appointmentRepository.save(appointment);

        // Create a COMPLETED order owned by userA
        Order order = new Order();
        order.setUserId(userAId);
        order.setAppointmentId(appointment.getId());
        order.setOrderStatus("COMPLETED");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("15.00"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        completedOrderId = order.getId();
    }

    @Test
    void shouldRejectReviewByNonOwner() {
        // userB attempts to create a review for userA's completed order — must be rejected
        assertThatThrownBy(() ->
            reviewService.createReview(completedOrderId, userBId, 5, "Trying to review someone else's order")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("owner");
    }

    @Test
    void shouldAllowReviewByOwner() {
        // userA creates a review for their own completed order — must succeed
        Review review = reviewService.createReview(completedOrderId, userAId, 4, "Good service");

        assertThat(review).isNotNull();
        assertThat(review.getId()).isNotNull();
        assertThat(review.getOrderId()).isEqualTo(completedOrderId);
        assertThat(review.getReviewerUserId()).isEqualTo(userAId);
        assertThat(review.getRating()).isEqualTo(4);
    }
}
