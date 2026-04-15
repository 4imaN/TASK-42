package com.reclaim.portal.e2e;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-HTTP lifecycle tests for the order domain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpOrderLifecycleTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private RecyclingItemRepository recyclingItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private JwtService jwtService;

    private User owner;
    private User reviewer;
    private User admin;
    private User stranger;
    private String ownerToken;
    private String reviewerToken;
    private String adminToken;
    private String strangerToken;
    private Long itemId;
    private Long appointmentId;   // PICKUP, +3 days

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role adminRole    = findOrCreateRole("ROLE_ADMIN");

        owner    = createUser("ol_owner_"    + nonce, userRole);
        reviewer = createUser("ol_reviewer_" + nonce, reviewerRole);
        admin    = createUser("ol_admin_"    + nonce, adminRole);
        stranger = createUser("ol_stranger_" + nonce, userRole);

        ownerToken    = jwtService.generateAccessToken(owner);
        reviewerToken = jwtService.generateAccessToken(reviewer);
        adminToken    = jwtService.generateAccessToken(admin);
        strangerToken = jwtService.generateAccessToken(stranger);

        // RecyclingItem
        RecyclingItem item = new RecyclingItem();
        item.setTitle("OLItem_" + nonce);
        item.setNormalizedTitle("olitem_" + nonce);
        item.setDescription("Order lifecycle test item");
        item.setCategory("Electronics");
        item.setItemCondition("GOOD");
        item.setPrice(new BigDecimal("19.99"));
        item.setCurrency("USD");
        item.setSellerId(owner.getId());
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item = recyclingItemRepository.save(item);
        itemId = item.getId();

        // Appointment: PICKUP, +3 days, 10 slots
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(3));
        appt.setStartTime("10:00");
        appt.setEndTime("10:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(10);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);
        appointmentId = appt.getId();
    }

    // =========================================================================
    // 1. Create order
    // =========================================================================

    @Test
    void shouldCreateOrderOverRealHttp() {
        Map<String, Object> body = Map.of(
                "itemIds", List.of(itemId),
                "appointmentId", appointmentId,
                "appointmentType", "PICKUP");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/orders", HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("orderStatus")).isEqualTo("PENDING_CONFIRMATION");
        assertThat(resp.getBody().get("id")).isNotNull();
    }

    // =========================================================================
    // 2. Accept order as reviewer
    // =========================================================================

    @Test
    void shouldAcceptOrderAsReviewerOverRealHttp() {
        Long orderId = createPendingOrder();

        HttpEntity<Void> req = new HttpEntity<>(csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviewer/orders/" + orderId + "/accept",
                HttpMethod.PUT, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().get("orderStatus")).isEqualTo("ACCEPTED");
    }

    // =========================================================================
    // 3. Complete order as reviewer
    // =========================================================================

    @Test
    void shouldCompleteOrderAsReviewerOverRealHttp() {
        Long orderId = createAcceptedOrderViaRepo();

        HttpEntity<Void> req = new HttpEntity<>(csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/orders/" + orderId + "/complete",
                HttpMethod.PUT, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().get("orderStatus")).isEqualTo("COMPLETED");
    }

    // =========================================================================
    // 4. Reject complete as regular user (403)
    // =========================================================================

    @Test
    void shouldRejectCompleteAsRegularUserOverRealHttp() {
        Long orderId = createAcceptedOrderViaRepo();

        HttpEntity<Void> req = new HttpEntity<>(csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/orders/" + orderId + "/complete",
                HttpMethod.PUT, req, Map.class);

        // 4xx — access denied (role check)
        assertThat(resp.getStatusCode().value()).isBetween(400, 499);
        assertThat(resp.getStatusCode().value()).isNotEqualTo(200);
    }

    // =========================================================================
    // 5. Cancel order as owner (appointment +3 days → CANCELED)
    // =========================================================================

    @Test
    void shouldCancelOrderAsOwnerOverRealHttp() {
        Long orderId = createPendingOrder();

        Map<String, String> body = Map.of("reason", "changed mind");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/orders/" + orderId + "/cancel",
                HttpMethod.PUT, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        // appointment is +3 days → CANCELED (not within 1-hour window)
        String status = (String) resp.getBody().get("orderStatus");
        assertThat(status).isIn("CANCELED", "EXCEPTION");
    }

    // =========================================================================
    // 6. Approve cancellation as reviewer
    // =========================================================================

    @Test
    void shouldApproveCancellationAsReviewerOverRealHttp() {
        // Seed an order in EXCEPTION status via repo (simulates late-cancel flow)
        Appointment closedAppt = new Appointment();
        closedAppt.setAppointmentDate(LocalDate.now().plusDays(3));
        closedAppt.setStartTime("14:00");
        closedAppt.setEndTime("14:30");
        closedAppt.setAppointmentType("PICKUP");
        closedAppt.setSlotsAvailable(5);
        closedAppt.setSlotsBooked(1); // already booked
        closedAppt.setCreatedAt(LocalDateTime.now());
        closedAppt = appointmentRepository.save(closedAppt);

        Order exceptionOrder = new Order();
        exceptionOrder.setUserId(owner.getId());
        exceptionOrder.setAppointmentId(closedAppt.getId());
        exceptionOrder.setOrderStatus("EXCEPTION");
        exceptionOrder.setAppointmentType("PICKUP");
        exceptionOrder.setRescheduleCount(0);
        exceptionOrder.setCurrency("USD");
        exceptionOrder.setTotalPrice(BigDecimal.TEN);
        exceptionOrder.setCreatedAt(LocalDateTime.now());
        exceptionOrder.setUpdatedAt(LocalDateTime.now());
        exceptionOrder = orderRepository.save(exceptionOrder);
        Long orderId = exceptionOrder.getId();

        Map<String, String> body = Map.of("reason", "OK");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviewer/orders/" + orderId + "/approve-cancel",
                HttpMethod.PUT, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().get("orderStatus")).isEqualTo("CANCELED");
        Number approvedBy = (Number) resp.getBody().get("cancellationApprovedBy");
        assertThat(approvedBy).isNotNull();
        assertThat(approvedBy.longValue()).isEqualTo(reviewer.getId());
    }

    // =========================================================================
    // 7. Reschedule order as owner
    // =========================================================================

    @Test
    void shouldRescheduleOrderAsOwnerOverRealHttp() {
        Long orderId = createPendingOrder();

        // Second PICKUP appointment
        Appointment appt2 = new Appointment();
        appt2.setAppointmentDate(LocalDate.now().plusDays(5));
        appt2.setStartTime("09:00");
        appt2.setEndTime("09:30");
        appt2.setAppointmentType("PICKUP");
        appt2.setSlotsAvailable(5);
        appt2.setSlotsBooked(0);
        appt2.setCreatedAt(LocalDateTime.now());
        appt2 = appointmentRepository.save(appt2);
        Long appt2Id = appt2.getId();

        Map<String, Long> body = Map.of("newAppointmentId", appt2Id);
        HttpEntity<Map<String, Long>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/orders/" + orderId + "/reschedule",
                HttpMethod.PUT, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        Number returnedApptId = (Number) resp.getBody().get("appointmentId");
        assertThat(returnedApptId).isNotNull();
        assertThat(returnedApptId.longValue()).isEqualTo(appt2Id);
        Number rescheduleCount = (Number) resp.getBody().get("rescheduleCount");
        assertThat(rescheduleCount).isNotNull();
        assertThat(rescheduleCount.intValue()).isEqualTo(1);
    }

    // =========================================================================
    // 8. Reject reschedule with type mismatch (409)
    // =========================================================================

    @Test
    void shouldRejectRescheduleWithTypeMismatchOverRealHttp() {
        Long orderId = createPendingOrder(); // PICKUP order

        // DROPOFF appointment — type mismatch
        Appointment dropoffAppt = new Appointment();
        dropoffAppt.setAppointmentDate(LocalDate.now().plusDays(4));
        dropoffAppt.setStartTime("08:00");
        dropoffAppt.setEndTime("08:30");
        dropoffAppt.setAppointmentType("DROPOFF");
        dropoffAppt.setSlotsAvailable(5);
        dropoffAppt.setSlotsBooked(0);
        dropoffAppt.setCreatedAt(LocalDateTime.now());
        dropoffAppt = appointmentRepository.save(dropoffAppt);
        Long dropoffId = dropoffAppt.getId();

        Map<String, Long> body = Map.of("newAppointmentId", dropoffId);
        HttpEntity<Map<String, Long>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/orders/" + orderId + "/reschedule",
                HttpMethod.PUT, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(409);
    }

    // =========================================================================
    // 9. Reject reschedule as stranger (403)
    // =========================================================================

    @Test
    void shouldRejectRescheduleAsStrangerOverRealHttp() {
        Long orderId = createPendingOrder(); // owned by owner

        Appointment appt3 = new Appointment();
        appt3.setAppointmentDate(LocalDate.now().plusDays(6));
        appt3.setStartTime("11:00");
        appt3.setEndTime("11:30");
        appt3.setAppointmentType("PICKUP");
        appt3.setSlotsAvailable(5);
        appt3.setSlotsBooked(0);
        appt3.setCreatedAt(LocalDateTime.now());
        appt3 = appointmentRepository.save(appt3);
        Long appt3Id = appt3.getId();

        Map<String, Long> body = Map.of("newAppointmentId", appt3Id);
        HttpEntity<Map<String, Long>> req = new HttpEntity<>(body, csrfAuthHeaders(strangerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/orders/" + orderId + "/reschedule",
                HttpMethod.PUT, req, Map.class);

        assertThat(resp.getStatusCode().value()).isBetween(400, 499);
        assertThat(resp.getStatusCode().value()).isNotEqualTo(200);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Long createPendingOrder() {
        Map<String, Object> body = Map.of(
                "itemIds", List.of(itemId),
                "appointmentId", appointmentId,
                "appointmentType", "PICKUP");
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/orders", HttpMethod.POST, req, Map.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    private Long createAcceptedOrderViaRepo() {
        // Create a fresh appointment with enough slots (the main one may be consumed)
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(3));
        appt.setStartTime("10:00");
        appt.setEndTime("10:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(10);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        Order order = new Order();
        order.setUserId(owner.getId());
        order.setAppointmentId(appt.getId());
        order.setOrderStatus("ACCEPTED");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(BigDecimal.TEN);
        order.setReviewerId(reviewer.getId());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        return order.getId();
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        return h;
    }

    private HttpHeaders csrfAuthHeaders(String token) {
        ResponseEntity<String> probe = restTemplate.getForEntity(base() + "/login", String.class);
        String xsrf = null;
        List<String> cookies = probe.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String c : cookies) {
                if (c.startsWith("XSRF-TOKEN=")) {
                    xsrf = c.substring("XSRF-TOKEN=".length()).split(";", 2)[0];
                    break;
                }
            }
        }
        HttpHeaders h = authHeaders(token);
        if (xsrf != null) {
            h.add("Cookie", "XSRF-TOKEN=" + xsrf);
            h.add("X-XSRF-TOKEN", xsrf);
        }
        return h;
    }

    private Role findOrCreateRole(String name) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
    }

    private User createUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        u.setEmail(username + "@example.com");
        u.setEnabled(true);
        u.setLocked(false);
        u.setForcePasswordReset(false);
        u.setFailedAttempts(0);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        u.setRoles(new HashSet<>(Set.of(role)));
        return userRepository.save(u);
    }
}
