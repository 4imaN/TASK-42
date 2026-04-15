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
import com.reclaim.portal.contracts.entity.ContractClauseField;
import com.reclaim.portal.contracts.entity.ContractTemplate;
import com.reclaim.portal.contracts.entity.ContractTemplateVersion;
import com.reclaim.portal.contracts.repository.ContractClauseFieldRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateVersionRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.entity.OrderItem;
import com.reclaim.portal.orders.repository.OrderItemRepository;
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
 * Real-HTTP tests for reviewer-specific endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpReviewerActionsTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private RecyclingItemRepository recyclingItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private ContractTemplateRepository contractTemplateRepository;
    @Autowired private ContractTemplateVersionRepository contractTemplateVersionRepository;
    @Autowired private ContractClauseFieldRepository contractClauseFieldRepository;
    @Autowired private JwtService jwtService;

    private User owner;
    private User reviewer;
    private User regularUser;
    private String reviewerToken;
    private String regularUserToken;
    private Long orderId;
    private Long orderItemId;
    private Long templateVersionId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role adminRole    = findOrCreateRole("ROLE_ADMIN");

        owner       = createUser("ra_owner_"    + nonce, userRole);
        reviewer    = createUser("ra_reviewer_" + nonce, reviewerRole);
        regularUser = createUser("ra_regular_"  + nonce, userRole);

        reviewerToken    = jwtService.generateAccessToken(reviewer);
        regularUserToken = jwtService.generateAccessToken(regularUser);

        // RecyclingItem
        RecyclingItem item = new RecyclingItem();
        item.setTitle("RAItem_" + nonce);
        item.setNormalizedTitle("raitem_" + nonce);
        item.setDescription("Reviewer actions test item");
        item.setCategory("Electronics");
        item.setItemCondition("GOOD");
        item.setPrice(new BigDecimal("9.99"));
        item.setCurrency("USD");
        item.setSellerId(owner.getId());
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item = recyclingItemRepository.save(item);

        // Appointment
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(3));
        appt.setStartTime("10:00");
        appt.setEndTime("10:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(10);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        // PENDING_CONFIRMATION order
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
        orderId = order.getId();

        // OrderItem attached to this order
        OrderItem oi = new OrderItem();
        oi.setOrderId(order.getId());
        oi.setItemId(item.getId());
        oi.setSnapshotTitle("RAItem_" + nonce);
        oi.setSnapshotCategory("Electronics");
        oi.setSnapshotCondition("GOOD");
        oi.setSnapshotPrice(new BigDecimal("9.99"));
        oi = orderItemRepository.save(oi);
        orderItemId = oi.getId();

        // ContractTemplate + Version + ClauseField with defaultValue
        User admin = createUser("ra_admin_" + nonce, adminRole);
        ContractTemplate template = new ContractTemplate();
        template.setName("RA Template " + nonce);
        template.setDescription("Template for reviewer actions tests");
        template.setActive(true);
        template.setCreatedBy(admin.getId());
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        template = contractTemplateRepository.save(template);

        ContractTemplateVersion version = new ContractTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNumber(1);
        version.setContent("Contract for {{partyName}}");
        version.setChangeNotes("Initial");
        version.setCreatedBy(admin.getId());
        version.setCreatedAt(LocalDateTime.now());
        version = contractTemplateVersionRepository.save(version);
        templateVersionId = version.getId();

        ContractClauseField field = new ContractClauseField();
        field.setTemplateVersionId(version.getId());
        field.setFieldName("partyName");
        field.setFieldType("TEXT");
        field.setFieldLabel("Party Name");
        field.setRequired(true);
        field.setDefaultValue("Default Party");
        field.setDisplayOrder(1);
        contractClauseFieldRepository.save(field);
    }

    // =========================================================================
    // 1. Get reviewer queue
    // =========================================================================

    @Test
    void shouldGetReviewerQueueOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(reviewerToken));
        ResponseEntity<List> resp = restTemplate.exchange(
                base() + "/api/reviewer/queue", HttpMethod.GET, req, List.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();

        // Our seeded PENDING_CONFIRMATION order should be present
        boolean found = ((List<?>) resp.getBody()).stream()
                .filter(o -> o instanceof Map)
                .map(o -> (Map<?, ?>) o)
                .anyMatch(o -> {
                    Object oid = o.get("id");
                    return oid != null && ((Number) oid).longValue() == orderId;
                });
        assertThat(found).as("queue should contain our seeded pending order").isTrue();
    }

    // =========================================================================
    // 2. Reject queue for regular user (403)
    // =========================================================================

    @Test
    void shouldRejectReviewerQueueForRegularUserOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(regularUserToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviewer/queue", HttpMethod.GET, req, Map.class);

        assertThat(resp.getStatusCode().value()).isBetween(400, 499);
        assertThat(resp.getStatusCode().value()).isNotEqualTo(200);
    }

    // =========================================================================
    // 3. Adjust category
    // =========================================================================

    @Test
    void shouldAdjustCategoryOverRealHttp() {
        Map<String, String> body = Map.of("newCategory", "Books");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviewer/order-items/" + orderItemId + "/adjust-category",
                HttpMethod.PUT, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("adjustedCategory")).isEqualTo("Books");
        Number adjustedBy = (Number) resp.getBody().get("adjustedBy");
        assertThat(adjustedBy).isNotNull();
        assertThat(adjustedBy.longValue()).isEqualTo(reviewer.getId());

        // Verify DB persisted
        OrderItem persisted = orderItemRepository.findById(orderItemId).orElseThrow();
        assertThat(persisted.getAdjustedCategory()).isEqualTo("Books");
    }

    // =========================================================================
    // 4. Accept order via reviewer endpoint
    // =========================================================================

    @Test
    void shouldAcceptOrderViaReviewerEndpointOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviewer/orders/" + orderId + "/accept",
                HttpMethod.PUT, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().get("orderStatus")).isEqualTo("ACCEPTED");
    }

    // =========================================================================
    // 5. Approve cancel via reviewer endpoint
    // =========================================================================

    @Test
    void shouldApproveCancelViaReviewerEndpointOverRealHttp() {
        // Set order to EXCEPTION via repo
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setOrderStatus("EXCEPTION");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        Map<String, String> body = Map.of("reason", "OK");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviewer/orders/" + orderId + "/approve-cancel",
                HttpMethod.PUT, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().get("orderStatus")).isEqualTo("CANCELED");
    }

    // =========================================================================
    // 6. Initiate contract via reviewer endpoint
    // =========================================================================

    @Test
    void shouldInitiateContractViaReviewerEndpointOverRealHttp() {
        // Set order to ACCEPTED first
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setOrderStatus("ACCEPTED");
        order.setReviewerId(reviewer.getId());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateVersionId", templateVersionId);
        body.put("userId", owner.getId());
        body.put("fieldValues", "partyName=TestParty");
        body.put("startDate", LocalDate.now().toString());
        body.put("endDate", LocalDate.now().plusDays(365).toString());

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> resp = restTemplate.exchange(
                base() + "/api/reviewer/orders/" + orderId + "/initiate-contract",
                HttpMethod.POST, req, Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("contractStatus")).isEqualTo("INITIATED");
        Number userId = (Number) resp.getBody().get("userId");
        assertThat(userId).isNotNull();
        assertThat(userId.longValue()).isEqualTo(owner.getId());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
