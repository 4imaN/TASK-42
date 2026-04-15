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
 * Real-HTTP lifecycle tests for the appeals domain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpAppealsFlowTest {

    // Minimal JPEG bytes
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
    @Autowired private JwtService jwtService;

    private User owner;
    private User reviewer;
    private User stranger;
    private String ownerToken;
    private String reviewerToken;
    private String strangerToken;
    private Long ownerOrderId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role adminRole    = findOrCreateRole("ROLE_ADMIN");

        owner    = createUser("af_owner_"    + nonce, userRole);
        reviewer = createUser("af_reviewer_" + nonce, reviewerRole);
        stranger = createUser("af_stranger_" + nonce, userRole);

        ownerToken    = jwtService.generateAccessToken(owner);
        reviewerToken = jwtService.generateAccessToken(reviewer);
        strangerToken = jwtService.generateAccessToken(stranger);

        // Appointment for owner's order
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(3));
        appt.setStartTime("10:00");
        appt.setEndTime("10:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(10);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        // Order owned by owner (PENDING_CONFIRMATION — appeals can be filed on any status)
        Order order = new Order();
        order.setUserId(owner.getId());
        order.setAppointmentId(appt.getId());
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(BigDecimal.TEN);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        ownerOrderId = order.getId();
    }

    // =========================================================================
    // 1. Create appeal
    // =========================================================================

    @Test
    void shouldCreateAppealOverRealHttp() {
        Map<String, Object> body = Map.of(
                "orderId", ownerOrderId,
                "reason", "issue");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/appeals", HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("appealStatus")).isEqualTo("OPEN");
        Number appellantId = (Number) resp.getBody().get("appellantId");
        assertThat(appellantId).isNotNull();
        assertThat(appellantId.longValue()).isEqualTo(owner.getId());
    }

    // =========================================================================
    // 2. Reject appeal for order not owned (403)
    // =========================================================================

    @Test
    void shouldRejectAppealForOrderNotOwnedOverRealHttp() {
        // Stranger tries to file an appeal for owner's order
        Map<String, Object> body = Map.of(
                "orderId", ownerOrderId,
                "reason", "unauthorized");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, csrfAuthHeaders(strangerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/appeals", HttpMethod.POST, req, Map.class);

        // Access denied → 409 (BusinessRuleException)
        assertThat(resp.getStatusCode().value()).isBetween(400, 499);
        assertThat(resp.getStatusCode().value()).isNotEqualTo(200);
    }

    // =========================================================================
    // 3. Upload evidence as appellant
    // =========================================================================

    @Test
    void shouldUploadEvidenceAsAppellantOverRealHttp() {
        Long appealId = createAppeal();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(MINIMAL_JPEG) {
            @Override public String getFilename() { return "evidence.jpg"; }
        });

        HttpHeaders mpHeaders = csrfAuthHeaders(ownerToken);
        mpHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, mpHeaders);
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/appeals/" + appealId + "/evidence",
                HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("fileName")).isNotNull();
        assertThat(resp.getBody().get("filePath")).isNotNull();
        assertThat(resp.getBody().get("checksum")).isNotNull();
    }

    // =========================================================================
    // 4. Reject evidence upload as stranger (403)
    // =========================================================================

    @Test
    void shouldRejectEvidenceUploadAsStrangerOverRealHttp() {
        Long appealId = createAppeal();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(MINIMAL_JPEG) {
            @Override public String getFilename() { return "bad.jpg"; }
        });

        HttpHeaders mpHeaders = csrfAuthHeaders(strangerToken);
        mpHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, mpHeaders);
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/appeals/" + appealId + "/evidence",
                HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isBetween(400, 499);
        assertThat(resp.getStatusCode().value()).isNotEqualTo(200);
    }

    // =========================================================================
    // 5. Resolve appeal as reviewer
    // =========================================================================

    @Test
    void shouldResolveAppealAsReviewerOverRealHttp() {
        Long appealId = createAppeal();

        Map<String, String> body = Map.of("outcome", "APPROVED", "reasoning", "Verified");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/appeals/" + appealId + "/resolve",
                HttpMethod.PUT, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("appealStatus")).isEqualTo("RESOLVED");
    }

    // =========================================================================
    // 6. Reject resolve as regular user (403)
    // =========================================================================

    @Test
    void shouldRejectResolveAsRegularUserOverRealHttp() {
        Long appealId = createAppeal();

        Map<String, String> body = Map.of("outcome", "APPROVED", "reasoning", "nope");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/appeals/" + appealId + "/resolve",
                HttpMethod.PUT, req, Map.class);

        assertThat(resp.getStatusCode().value()).isBetween(400, 499);
        assertThat(resp.getStatusCode().value()).isNotEqualTo(200);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Creates a new order and appeal (each test needs fresh data to avoid conflicts). */
    private Long createAppeal() {
        // Fresh appointment + order per call to avoid conflicts across tests
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(4));
        appt.setStartTime("11:00");
        appt.setEndTime("11:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(5);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        Order order = new Order();
        order.setUserId(owner.getId());
        order.setAppointmentId(appt.getId());
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(BigDecimal.TEN);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        Map<String, Object> body = Map.of("orderId", order.getId(), "reason", "test");
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/appeals", HttpMethod.POST, req, Map.class);
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
