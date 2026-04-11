package com.reclaim.portal.service;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.entity.OrderOperationLog;
import com.reclaim.portal.orders.repository.OrderOperationLogRepository;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.orders.service.OrderService;
import jakarta.persistence.EntityManager;
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
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private RecyclingItemRepository recyclingItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderOperationLogRepository orderOperationLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Appointment pickupAppointment;
    private RecyclingItem item1;
    private Long USER_ID;
    private Long REVIEWER_ID;

    @BeforeEach
    void setUp() {
        // Create real users for FK constraints
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        Role reviewerRole = roleRepository.findByName("ROLE_REVIEWER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_REVIEWER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User orderUser = new User();
        orderUser.setUsername("order_user_" + System.nanoTime());
        orderUser.setPasswordHash(passwordEncoder.encode("Pass1!wordXXX"));
        orderUser.setEnabled(true);
        orderUser.setLocked(false);
        orderUser.setForcePasswordReset(false);
        orderUser.setFailedAttempts(0);
        orderUser.setCreatedAt(LocalDateTime.now());
        orderUser.setUpdatedAt(LocalDateTime.now());
        orderUser.setRoles(new HashSet<>(Set.of(userRole)));
        orderUser = userRepository.save(orderUser);
        USER_ID = orderUser.getId();

        User reviewerUser = new User();
        reviewerUser.setUsername("order_reviewer_" + System.nanoTime());
        reviewerUser.setPasswordHash(passwordEncoder.encode("Pass1!wordXXX"));
        reviewerUser.setEnabled(true);
        reviewerUser.setLocked(false);
        reviewerUser.setForcePasswordReset(false);
        reviewerUser.setFailedAttempts(0);
        reviewerUser.setCreatedAt(LocalDateTime.now());
        reviewerUser.setUpdatedAt(LocalDateTime.now());
        reviewerUser.setRoles(new HashSet<>(Set.of(reviewerRole)));
        reviewerUser = userRepository.save(reviewerUser);
        REVIEWER_ID = reviewerUser.getId();

        // Create an appointment 3 days from now (satisfies minAdvanceHours=2)
        LocalDate futureDate = LocalDate.now().plusDays(3);

        pickupAppointment = new Appointment();
        pickupAppointment.setAppointmentDate(futureDate);
        pickupAppointment.setStartTime("10:00");
        pickupAppointment.setEndTime("10:30");
        pickupAppointment.setAppointmentType("PICKUP");
        pickupAppointment.setSlotsAvailable(5);
        pickupAppointment.setSlotsBooked(0);
        pickupAppointment.setCreatedAt(LocalDateTime.now());
        pickupAppointment = appointmentRepository.save(pickupAppointment);

        // Create a catalog item
        item1 = new RecyclingItem();
        item1.setTitle("Aluminum Cans");
        item1.setNormalizedTitle("aluminum cans");
        item1.setCategory("METAL");
        item1.setItemCondition("GOOD");
        item1.setPrice(new BigDecimal("5.00"));
        item1.setCurrency("USD");
        item1.setActive(true);
        item1.setCreatedAt(LocalDateTime.now());
        item1.setUpdatedAt(LocalDateTime.now());
        item1 = recyclingItemRepository.save(item1);
    }

    @Test
    void shouldCreateOrder() {
        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");

        assertThat(order).isNotNull();
        assertThat(order.getId()).isNotNull();
        assertThat(order.getOrderStatus()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(order.getUserId()).isEqualTo(USER_ID);
        assertThat(order.getTotalPrice()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void shouldAcceptOrder() {
        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");

        Order accepted = orderService.acceptOrder(order.getId(), REVIEWER_ID);

        assertThat(accepted.getOrderStatus()).isEqualTo("ACCEPTED");
        assertThat(accepted.getReviewerId()).isEqualTo(REVIEWER_ID);
    }

    @Test
    void shouldCompleteOrder() {
        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");
        order = orderService.acceptOrder(order.getId(), REVIEWER_ID);

        Order completed = orderService.completeOrder(order.getId(), REVIEWER_ID);

        assertThat(completed.getOrderStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void shouldCancelOrder() {
        // Use an appointment far in the future (> 1 hour) so cancellation proceeds normally
        LocalDate farFuture = LocalDate.now().plusDays(7);
        Appointment farAppointment = new Appointment();
        farAppointment.setAppointmentDate(farFuture);
        farAppointment.setStartTime("14:00");
        farAppointment.setEndTime("14:30");
        farAppointment.setAppointmentType("PICKUP");
        farAppointment.setSlotsAvailable(5);
        farAppointment.setSlotsBooked(0);
        farAppointment.setCreatedAt(LocalDateTime.now());
        farAppointment = appointmentRepository.save(farAppointment);

        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), farAppointment.getId(), "PICKUP");

        Order canceled = orderService.cancelOrder(order.getId(), USER_ID, "No longer needed");

        assertThat(canceled.getOrderStatus()).isEqualTo("CANCELED");
        assertThat(canceled.getCancellationReason()).isEqualTo("No longer needed");
    }

    @Test
    void shouldRejectThirdReschedule() {
        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");

        // Create two extra appointment slots for rescheduling
        LocalDate futureDate = LocalDate.now().plusDays(4);
        Appointment appt2 = new Appointment();
        appt2.setAppointmentDate(futureDate);
        appt2.setStartTime("11:00");
        appt2.setEndTime("11:30");
        appt2.setAppointmentType("PICKUP");
        appt2.setSlotsAvailable(5);
        appt2.setSlotsBooked(0);
        appt2.setCreatedAt(LocalDateTime.now());
        appt2 = appointmentRepository.save(appt2);

        Appointment appt3 = new Appointment();
        appt3.setAppointmentDate(futureDate);
        appt3.setStartTime("12:00");
        appt3.setEndTime("12:30");
        appt3.setAppointmentType("PICKUP");
        appt3.setSlotsAvailable(5);
        appt3.setSlotsBooked(0);
        appt3.setCreatedAt(LocalDateTime.now());
        appt3 = appointmentRepository.save(appt3);

        Appointment appt4 = new Appointment();
        appt4.setAppointmentDate(futureDate);
        appt4.setStartTime("13:00");
        appt4.setEndTime("13:30");
        appt4.setAppointmentType("PICKUP");
        appt4.setSlotsAvailable(5);
        appt4.setSlotsBooked(0);
        appt4.setCreatedAt(LocalDateTime.now());
        appt4 = appointmentRepository.save(appt4);

        // First reschedule
        order = orderService.rescheduleOrder(order.getId(), USER_ID, appt2.getId());
        assertThat(order.getRescheduleCount()).isEqualTo(1);

        // Second reschedule
        order = orderService.rescheduleOrder(order.getId(), USER_ID, appt3.getId());
        assertThat(order.getRescheduleCount()).isEqualTo(2);

        // Third reschedule should fail
        final Long orderId = order.getId();
        final Long appt4Id = appt4.getId();
        assertThatThrownBy(() -> orderService.rescheduleOrder(orderId, USER_ID, appt4Id))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("maximum");
    }

    @Test
    void shouldLogOperations() {
        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");
        orderService.acceptOrder(order.getId(), REVIEWER_ID);

        Map<String, Object> result = orderService.getOrderWithLogs(order.getId());

        @SuppressWarnings("unchecked")
        List<OrderOperationLog> logs = (List<OrderOperationLog>) result.get("logs");
        assertThat(logs).isNotEmpty();
        // Should have at least ORDER_CREATED and ORDER_ACCEPTED
        assertThat(logs).extracting(OrderOperationLog::getOperation)
            .contains("ORDER_CREATED", "ORDER_ACCEPTED");
    }

    @Test
    void shouldRejectOrderCreationWithTypeMismatch() {
        // Appointment is PICKUP but order says DROPOFF
        assertThatThrownBy(() ->
            orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "DROPOFF")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("mismatch");
    }

    @Test
    void shouldRejectCompletionWhenNotAccepted() {
        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");

        assertThatThrownBy(() -> orderService.completeOrder(order.getId(), REVIEWER_ID))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldFlagExceptionWhenCancelledWithinOneHour() {
        // Create appointment starting in 30 minutes (< 1 hour)
        LocalDate today = LocalDate.now();
        java.time.LocalTime soonTime = java.time.LocalTime.now().plusMinutes(30);
        // Format as HH:mm
        String startTimeStr = soonTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        java.time.LocalTime endTime = soonTime.plusMinutes(30);
        String endTimeStr = endTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

        Appointment soonAppointment = new Appointment();
        soonAppointment.setAppointmentDate(today);
        soonAppointment.setStartTime(startTimeStr);
        soonAppointment.setEndTime(endTimeStr);
        soonAppointment.setAppointmentType("PICKUP");
        soonAppointment.setSlotsAvailable(5);
        soonAppointment.setSlotsBooked(1); // pre-booked
        soonAppointment.setCreatedAt(LocalDateTime.now());
        soonAppointment = appointmentRepository.save(soonAppointment);

        // Create order directly (bypass createOrder validation) to avoid advance-time check
        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setUserId(USER_ID);
        order.setAppointmentId(soonAppointment.getId());
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("5.00"));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = orderRepository.save(order);

        Order cancelled = orderService.cancelOrder(order.getId(), USER_ID, "Emergency cancellation");

        // Should be in EXCEPTION state because appointment is < 1 hour away
        assertThat(cancelled.getOrderStatus()).isEqualTo("EXCEPTION");
        assertThat(cancelled.getCancellationReason()).isEqualTo("Emergency cancellation");
    }

    @Test
    void shouldApproveCancellationFromExceptionState() {
        // Set up order in EXCEPTION state
        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setUserId(USER_ID);
        order.setAppointmentId(pickupAppointment.getId());
        order.setOrderStatus("EXCEPTION");
        order.setCancellationReason("User requested");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("5.00"));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = orderRepository.save(order);

        // Bump slotsBooked so releaseSlot works correctly
        pickupAppointment.setSlotsBooked(1);
        appointmentRepository.save(pickupAppointment);

        Order approved = orderService.approveCancellation(order.getId(), REVIEWER_ID, "Approved by admin");

        assertThat(approved.getOrderStatus()).isEqualTo("CANCELED");
        assertThat(approved.getCancellationApprovedBy()).isEqualTo(REVIEWER_ID);
        assertThat(approved.getCancellationReason()).isEqualTo("Approved by admin");
    }

    @Test
    void shouldApproveCancellationWithNullReasonFallsBackToOriginal() {
        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setUserId(USER_ID);
        order.setAppointmentId(pickupAppointment.getId());
        order.setOrderStatus("EXCEPTION");
        order.setCancellationReason("Original reason");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("5.00"));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = orderRepository.save(order);

        pickupAppointment.setSlotsBooked(1);
        appointmentRepository.save(pickupAppointment);

        Order approved = orderService.approveCancellation(order.getId(), REVIEWER_ID, null);

        assertThat(approved.getCancellationReason()).isEqualTo("Original reason");
    }

    @Test
    void shouldRejectApproveCancellationWhenNotInExceptionState() {
        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");

        assertThatThrownBy(() -> orderService.approveCancellation(order.getId(), REVIEWER_ID, "reason"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("EXCEPTION");
    }

    @Test
    void shouldRescheduleFromAcceptedState() {
        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");
        order = orderService.acceptOrder(order.getId(), REVIEWER_ID);

        LocalDate futureDate = LocalDate.now().plusDays(5);
        Appointment newAppt = new Appointment();
        newAppt.setAppointmentDate(futureDate);
        newAppt.setStartTime("15:00");
        newAppt.setEndTime("15:30");
        newAppt.setAppointmentType("PICKUP");
        newAppt.setSlotsAvailable(5);
        newAppt.setSlotsBooked(0);
        newAppt.setCreatedAt(LocalDateTime.now());
        newAppt = appointmentRepository.save(newAppt);

        Order rescheduled = orderService.rescheduleOrder(order.getId(), USER_ID, newAppt.getId());

        assertThat(rescheduled.getOrderStatus()).isEqualTo("ACCEPTED");
        assertThat(rescheduled.getRescheduleCount()).isEqualTo(1);
        assertThat(rescheduled.getAppointmentId()).isEqualTo(newAppt.getId());
    }

    @Test
    void shouldRejectRescheduleWhenCancelled() {
        LocalDate farFuture = LocalDate.now().plusDays(7);
        Appointment farAppt = new Appointment();
        farAppt.setAppointmentDate(farFuture);
        farAppt.setStartTime("09:00");
        farAppt.setEndTime("09:30");
        farAppt.setAppointmentType("PICKUP");
        farAppt.setSlotsAvailable(5);
        farAppt.setSlotsBooked(0);
        farAppt.setCreatedAt(LocalDateTime.now());
        farAppt = appointmentRepository.save(farAppt);

        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), farAppt.getId(), "PICKUP");
        orderService.cancelOrder(order.getId(), USER_ID, "No longer needed");

        LocalDate anotherDate = LocalDate.now().plusDays(6);
        Appointment anotherAppt = new Appointment();
        anotherAppt.setAppointmentDate(anotherDate);
        anotherAppt.setStartTime("10:00");
        anotherAppt.setEndTime("10:30");
        anotherAppt.setAppointmentType("PICKUP");
        anotherAppt.setSlotsAvailable(5);
        anotherAppt.setSlotsBooked(0);
        anotherAppt.setCreatedAt(LocalDateTime.now());
        anotherAppt = appointmentRepository.save(anotherAppt);

        final Long cancelledOrderId = order.getId();
        final Long anotherApptId = anotherAppt.getId();

        assertThatThrownBy(() -> orderService.rescheduleOrder(cancelledOrderId, USER_ID, anotherApptId))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("reschedule");
    }

    @Test
    void shouldGetOrdersByUser() {
        orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");

        List<Order> orders = orderService.getOrdersByUser(USER_ID);
        assertThat(orders).isNotEmpty();
        assertThat(orders).allMatch(o -> USER_ID.equals(o.getUserId()));
    }

    @Test
    void shouldGetReviewerQueue() {
        orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");

        List<Order> queue = orderService.getReviewerQueue();
        assertThat(queue).isNotEmpty();
        assertThat(queue).allMatch(o -> "PENDING_CONFIRMATION".equals(o.getOrderStatus()));
    }

    @Test
    void shouldRejectCancelWhenCompleted() {
        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");
        order = orderService.acceptOrder(order.getId(), REVIEWER_ID);
        orderService.completeOrder(order.getId(), REVIEWER_ID);

        final Long completedOrderId = order.getId();
        assertThatThrownBy(() -> orderService.cancelOrder(completedOrderId, USER_ID, "Too late"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Cannot cancel");
    }

    @Test
    void shouldRejectAcceptWhenAlreadyAccepted() {
        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");
        orderService.acceptOrder(order.getId(), REVIEWER_ID);

        assertThatThrownBy(() -> orderService.acceptOrder(order.getId(), REVIEWER_ID))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("PENDING_CONFIRMATION");
    }

    @Test
    void shouldPreventOperationLogUpdate() {
        // Create an order which logs ORDER_CREATED
        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");

        List<OrderOperationLog> logs = orderOperationLogRepository
            .findByOrderIdOrderByCreatedAtAsc(order.getId());
        assertThat(logs).isNotEmpty();

        OrderOperationLog log = logs.get(0);
        log.setDetails("tampered details");

        // Flushing should trigger @PreUpdate and throw
        assertThatThrownBy(() -> {
            entityManager.merge(log);
            entityManager.flush();
        }).satisfiesAnyOf(
            t -> assertThat(t).isInstanceOf(UnsupportedOperationException.class),
            t -> assertThat(t).hasRootCauseInstanceOf(UnsupportedOperationException.class)
        );
    }

    @Test
    void shouldPreventOperationLogDelete() {
        Order order = orderService.createOrder(USER_ID, List.of(item1.getId()), pickupAppointment.getId(), "PICKUP");

        List<OrderOperationLog> logs = orderOperationLogRepository
            .findByOrderIdOrderByCreatedAtAsc(order.getId());
        assertThat(logs).isNotEmpty();

        OrderOperationLog log = logs.get(0);

        // Removing should trigger @PreRemove and throw
        assertThatThrownBy(() -> {
            OrderOperationLog attached = entityManager.find(OrderOperationLog.class, log.getId());
            entityManager.remove(attached);
            entityManager.flush();
        }).satisfiesAnyOf(
            t -> assertThat(t).isInstanceOf(UnsupportedOperationException.class),
            t -> assertThat(t).hasRootCauseInstanceOf(UnsupportedOperationException.class)
        );
    }
}
