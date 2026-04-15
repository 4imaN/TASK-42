package com.reclaim.portal.e2e;

import com.reclaim.portal.appeals.entity.Appeal;
import com.reclaim.portal.appeals.repository.AppealRepository;
import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.reviews.entity.Review;
import com.reclaim.portal.reviews.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
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
 * Real-HTTP coverage for review listing/retrieval and appeal evidence upload.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpReviewsAndAppealsEvidenceTest {

    // Minimal JPEG magic bytes
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
    @Autowired private AppealRepository appealRepository;
    @Autowired private JwtService jwtService;

    private User owner;
    private User reviewer;
    private String ownerToken;
    private Long reviewOrderId;
    private Long otherOrderId;
    private Long appealId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");

        owner    = createUser("rev_owner_"    + nonce, userRole);
        reviewer = createUser("rev_reviewer_" + nonce, reviewerRole);

        ownerToken = jwtService.generateAccessToken(owner);

        // Appointment for the seeded completed order
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(5));
        appt.setStartTime("13:00");
        appt.setEndTime("13:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(5);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        // Completed order with a seeded review
        Order completedOrder = new Order();
        completedOrder.setUserId(owner.getId());
        completedOrder.setAppointmentId(appt.getId());
        completedOrder.setOrderStatus("COMPLETED");
        completedOrder.setAppointmentType("PICKUP");
        completedOrder.setRescheduleCount(0);
        completedOrder.setCurrency("USD");
        completedOrder.setTotalPrice(BigDecimal.TEN);
        completedOrder.setReviewerId(reviewer.getId());
        completedOrder.setCreatedAt(LocalDateTime.now());
        completedOrder.setUpdatedAt(LocalDateTime.now());
        completedOrder = orderRepository.save(completedOrder);
        reviewOrderId = completedOrder.getId();

        // Seed a review for completedOrder
        Review review = new Review();
        review.setOrderId(reviewOrderId);
        review.setReviewerUserId(owner.getId());
        review.setRating(5);
        review.setReviewText("Great service for evidence test!");
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());
        reviewRepository.save(review);

        // A second completed order without a review (for the 404 test)
        Appointment appt2 = new Appointment();
        appt2.setAppointmentDate(LocalDate.now().plusDays(6));
        appt2.setStartTime("14:00");
        appt2.setEndTime("14:30");
        appt2.setAppointmentType("PICKUP");
        appt2.setSlotsAvailable(5);
        appt2.setSlotsBooked(0);
        appt2.setCreatedAt(LocalDateTime.now());
        appt2 = appointmentRepository.save(appt2);

        Order otherOrder = new Order();
        otherOrder.setUserId(owner.getId());
        otherOrder.setAppointmentId(appt2.getId());
        otherOrder.setOrderStatus("COMPLETED");
        otherOrder.setAppointmentType("PICKUP");
        otherOrder.setRescheduleCount(0);
        otherOrder.setCurrency("USD");
        otherOrder.setTotalPrice(BigDecimal.TEN);
        otherOrder.setReviewerId(reviewer.getId());
        otherOrder.setCreatedAt(LocalDateTime.now());
        otherOrder.setUpdatedAt(LocalDateTime.now());
        otherOrder = orderRepository.save(otherOrder);
        otherOrderId = otherOrder.getId();

        // Seed an appeal for the completed order
        Appeal appeal = new Appeal();
        appeal.setOrderId(reviewOrderId);
        appeal.setAppellantId(owner.getId());
        appeal.setReason("Test appeal for evidence upload");
        appeal.setAppealStatus("OPEN");
        appeal.setCreatedAt(LocalDateTime.now());
        appeal.setUpdatedAt(LocalDateTime.now());
        appeal = appealRepository.save(appeal);
        appealId = appeal.getId();
    }

    // =========================================================================
    // 1. List my reviews
    // =========================================================================

    @Test
    void shouldListMyReviewsOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(ownerToken));
        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                base() + "/api/reviews/my",
                HttpMethod.GET, req,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull().isNotEmpty();

        boolean foundReview = resp.getBody().stream()
                .anyMatch(r -> {
                    Object oid = r.get("orderId");
                    return oid != null
                        && reviewOrderId.longValue() == ((Number) oid).longValue();
                });
        assertThat(foundReview).as("my reviews should contain the seeded review for orderId=" + reviewOrderId).isTrue();
    }

    // =========================================================================
    // 2. Get review for a specific order
    // =========================================================================

    @Test
    void shouldGetReviewForOrderOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(ownerToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/reviews/order/" + reviewOrderId,
                HttpMethod.GET, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).containsKey("review");
        assertThat(resp.getBody()).containsKey("images");
    }

    // =========================================================================
    // 3. 404 when no review exists for an order
    // =========================================================================

    @Test
    void shouldReturn404ForReviewOfOrderWithoutReviewOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(ownerToken));
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/reviews/order/" + otherOrderId,
                HttpMethod.GET, req, String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // =========================================================================
    // 4. Upload appeal evidence as multipart
    // =========================================================================

    @Test
    void shouldUploadAppealEvidenceOverRealHttp() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(MINIMAL_JPEG) {
            @Override
            public String getFilename() { return "test.jpg"; }
        });

        HttpHeaders headers = csrfAuthHeaders(ownerToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/appeals/" + appealId + "/evidence",
                HttpMethod.POST, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("fileName")).isNotNull();
        assertThat(resp.getBody().get("filePath")).isNotNull();
        assertThat(resp.getBody().get("checksum")).isNotNull();
    }

    // =========================================================================
    // 5. Get appeal details shows uploaded evidence
    // =========================================================================

    @Test
    void shouldListAppealEvidenceOverRealHttp() {
        // Upload evidence first
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(MINIMAL_JPEG) {
            @Override
            public String getFilename() { return "test.jpg"; }
        });
        HttpHeaders uploadHeaders = csrfAuthHeaders(ownerToken);
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        restTemplate.exchange(
                base() + "/api/appeals/" + appealId + "/evidence",
                HttpMethod.POST, new HttpEntity<>(body, uploadHeaders),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        // Now GET the appeal details
        HttpEntity<Void> getReq = new HttpEntity<>(authHeaders(ownerToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/appeals/" + appealId,
                HttpMethod.GET, getReq,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).containsKey("appeal");
        assertThat(resp.getBody()).containsKey("outcome");

        List<?> evidence = (List<?>) resp.getBody().get("evidence");
        assertThat(evidence).isNotNull().isNotEmpty();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) {
            h.setBearerAuth(token);
        }
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
