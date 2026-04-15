package com.reclaim.portal.e2e;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.reviews.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-HTTP lifecycle tests for the review domain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpReviewLifecycleTest {

    // Minimal valid JPEG bytes
    private static final byte[] MINIMAL_JPEG = {
        (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0x00,0x10,0x4A,0x46,
        0x49,0x46,0x00,0x01,0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00
    };

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private JwtService jwtService;

    private User owner;
    private User reviewer;
    private String ownerToken;
    private Long completedOrderId;
    private Long pendingOrderId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");

        owner    = createUser("rl_owner_"    + nonce, userRole);
        reviewer = createUser("rl_reviewer_" + nonce, reviewerRole);

        ownerToken = jwtService.generateAccessToken(owner);

        // Appointment for completed order
        Appointment appt1 = new Appointment();
        appt1.setAppointmentDate(LocalDate.now().plusDays(5));
        appt1.setStartTime("10:00");
        appt1.setEndTime("10:30");
        appt1.setAppointmentType("PICKUP");
        appt1.setSlotsAvailable(5);
        appt1.setSlotsBooked(0);
        appt1.setCreatedAt(LocalDateTime.now());
        appt1 = appointmentRepository.save(appt1);

        // COMPLETED order owned by owner
        Order completedOrder = new Order();
        completedOrder.setUserId(owner.getId());
        completedOrder.setAppointmentId(appt1.getId());
        completedOrder.setOrderStatus("COMPLETED");
        completedOrder.setAppointmentType("PICKUP");
        completedOrder.setRescheduleCount(0);
        completedOrder.setCurrency("USD");
        completedOrder.setTotalPrice(BigDecimal.TEN);
        completedOrder.setReviewerId(reviewer.getId());
        completedOrder.setCreatedAt(LocalDateTime.now());
        completedOrder.setUpdatedAt(LocalDateTime.now());
        completedOrder = orderRepository.save(completedOrder);
        completedOrderId = completedOrder.getId();

        // Appointment for pending order
        Appointment appt2 = new Appointment();
        appt2.setAppointmentDate(LocalDate.now().plusDays(3));
        appt2.setStartTime("11:00");
        appt2.setEndTime("11:30");
        appt2.setAppointmentType("PICKUP");
        appt2.setSlotsAvailable(5);
        appt2.setSlotsBooked(0);
        appt2.setCreatedAt(LocalDateTime.now());
        appt2 = appointmentRepository.save(appt2);

        // PENDING order
        Order pendingOrder = new Order();
        pendingOrder.setUserId(owner.getId());
        pendingOrder.setAppointmentId(appt2.getId());
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

    // =========================================================================
    // 1. Create review for completed order
    // =========================================================================

    @Test
    void shouldCreateReviewForCompletedOrderOverRealHttp() {
        Map<String, Object> body = Map.of(
                "orderId", completedOrderId,
                "rating", 5,
                "reviewText", "Great");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviews", HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("id")).isNotNull();
        Number rating = (Number) resp.getBody().get("rating");
        assertThat(rating.intValue()).isEqualTo(5);
        assertThat(resp.getBody().get("reviewText")).isEqualTo("Great");
        Number reviewerUserId = (Number) resp.getBody().get("reviewerUserId");
        assertThat(reviewerUserId.longValue()).isEqualTo(owner.getId());
    }

    // =========================================================================
    // 2. Reject review for non-completed order (409)
    // =========================================================================

    @Test
    void shouldRejectReviewForNonCompletedOrderOverRealHttp() {
        Map<String, Object> body = Map.of(
                "orderId", pendingOrderId,
                "rating", 4,
                "reviewText", "Should not work");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviews", HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(409);
    }

    // =========================================================================
    // 3. Reject review with invalid rating (400)
    // =========================================================================

    @Test
    void shouldRejectReviewWithInvalidRatingOverRealHttp() {
        // Create a second completed order so rating-6 is the only failure
        Appointment apptExtra = new Appointment();
        apptExtra.setAppointmentDate(LocalDate.now().plusDays(7));
        apptExtra.setStartTime("12:00");
        apptExtra.setEndTime("12:30");
        apptExtra.setAppointmentType("PICKUP");
        apptExtra.setSlotsAvailable(5);
        apptExtra.setSlotsBooked(0);
        apptExtra.setCreatedAt(LocalDateTime.now());
        apptExtra = appointmentRepository.save(apptExtra);

        Order extra = new Order();
        extra.setUserId(owner.getId());
        extra.setAppointmentId(apptExtra.getId());
        extra.setOrderStatus("COMPLETED");
        extra.setAppointmentType("PICKUP");
        extra.setRescheduleCount(0);
        extra.setCurrency("USD");
        extra.setTotalPrice(BigDecimal.TEN);
        extra.setReviewerId(reviewer.getId());
        extra.setCreatedAt(LocalDateTime.now());
        extra.setUpdatedAt(LocalDateTime.now());
        extra = orderRepository.save(extra);

        Map<String, Object> body = Map.of(
                "orderId", extra.getId(),
                "rating", 6,
                "reviewText", "Bad rating");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviews", HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // =========================================================================
    // 4. Upload review image
    // =========================================================================

    @Test
    void shouldUploadReviewImageOverRealHttp() {
        Long reviewId = createReviewForCompletedOrder();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(MINIMAL_JPEG) {
            @Override public String getFilename() { return "review.jpg"; }
        });

        HttpHeaders mpHeaders = csrfAuthHeaders(ownerToken);
        mpHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, mpHeaders);
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviews/" + reviewId + "/images",
                HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("fileName")).isNotNull();
        assertThat(resp.getBody().get("filePath")).isNotNull();
        assertThat(resp.getBody().get("checksum")).isNotNull();
    }

    // =========================================================================
    // 5. Reject sixth image (409)
    // =========================================================================

    @Test
    void shouldRejectSixthImageOverRealHttp() {
        Long reviewId = createReviewForCompletedOrder();

        // Upload 5 images first
        for (int i = 0; i < 5; i++) {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(MINIMAL_JPEG) {
                @Override public String getFilename() { return "img.jpg"; }
            });
            HttpHeaders mpHeaders = csrfAuthHeaders(ownerToken);
            mpHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, mpHeaders);
            ResponseEntity<Map> upload = restTemplate.exchange(
                    base() + "/api/reviews/" + reviewId + "/images",
                    HttpMethod.POST, req, Map.class);
            assertThat(upload.getStatusCode().value()).isEqualTo(200);
        }

        // 6th should fail
        MultiValueMap<String, Object> body6 = new LinkedMultiValueMap<>();
        body6.add("file", new ByteArrayResource(MINIMAL_JPEG) {
            @Override public String getFilename() { return "sixth.jpg"; }
        });
        HttpHeaders mpHeaders6 = csrfAuthHeaders(ownerToken);
        mpHeaders6.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> req6 = new HttpEntity<>(body6, mpHeaders6);
        ResponseEntity<Map> resp6 = restTemplate.exchange(
                base() + "/api/reviews/" + reviewId + "/images",
                HttpMethod.POST, req6, Map.class);

        assertThat(resp6.getStatusCode().value()).isEqualTo(409);
    }

    // =========================================================================
    // 6. List my reviews
    // =========================================================================

    @Test
    void shouldListMyReviewsOverRealHttp() {
        // Create a review first
        Map<String, Object> body = Map.of(
                "orderId", completedOrderId,
                "rating", 4,
                "reviewText", "Nice");
        HttpEntity<Map<String, Object>> createReq = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> createResp = restTemplate.exchange(
                base() + "/api/reviews", HttpMethod.POST, createReq, Map.class);
        assertThat(createResp.getStatusCode().value()).isEqualTo(200);

        // GET /api/reviews/my
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(ownerToken));
        ResponseEntity<List> resp = restTemplate.exchange(
                base() + "/api/reviews/my", HttpMethod.GET, req, List.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).isNotEmpty();

        boolean found = ((List<?>) resp.getBody()).stream()
                .filter(r -> r instanceof Map)
                .map(r -> (Map<?, ?>) r)
                .anyMatch(r -> {
                    Object oid = r.get("orderId");
                    return oid != null
                            && ((Number) oid).longValue() == completedOrderId;
                });
        assertThat(found).as("my-reviews should contain the review for completedOrderId").isTrue();
    }

    // =========================================================================
    // 7. Get review for order
    // =========================================================================

    @Test
    void shouldGetReviewForOrderOverRealHttp() {
        // Create review first
        Map<String, Object> body = Map.of(
                "orderId", completedOrderId,
                "rating", 3,
                "reviewText", "OK");
        HttpEntity<Map<String, Object>> createReq = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> createResp = restTemplate.exchange(
                base() + "/api/reviews", HttpMethod.POST, createReq, Map.class);
        assertThat(createResp.getStatusCode().value()).isEqualTo(200);

        // GET /api/reviews/order/{orderId}
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviews/order/" + completedOrderId,
                HttpMethod.GET, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).containsKey("review");
        assertThat(resp.getBody()).containsKey("images");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Long createReviewForCompletedOrder() {
        // Use a fresh completed order to avoid duplicate-review constraint
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(8));
        appt.setStartTime("13:00");
        appt.setEndTime("13:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(5);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        Order order = new Order();
        order.setUserId(owner.getId());
        order.setAppointmentId(appt.getId());
        order.setOrderStatus("COMPLETED");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(BigDecimal.TEN);
        order.setReviewerId(reviewer.getId());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        Map<String, Object> body = Map.of(
                "orderId", order.getId(),
                "rating", 5,
                "reviewText", "Image test");
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviews", HttpMethod.POST, req, Map.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        return ((Number) resp.getBody().get("id")).longValue();
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
