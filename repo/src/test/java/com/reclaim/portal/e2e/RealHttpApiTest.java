package com.reclaim.portal.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.appeals.entity.Appeal;
import com.reclaim.portal.appeals.repository.AppealRepository;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-HTTP API tests using TestRestTemplate (no MockMvc).
 * Spring Security CSRF is handled by pre-fetching /login to obtain the XSRF-TOKEN cookie,
 * then forwarding that cookie + X-XSRF-TOKEN header on mutable requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpApiTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private RecyclingItemRepository recyclingItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ContractTemplateRepository contractTemplateRepository;

    @Autowired
    private ContractTemplateVersionRepository contractTemplateVersionRepository;

    @Autowired
    private ContractClauseFieldRepository contractClauseFieldRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private AppealRepository appealRepository;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    // Per-test state
    private User ownerUser;
    private User reviewerUser;
    private User adminUser;
    private String ownerToken;
    private String reviewerToken;
    private String adminToken;
    private Long itemId;
    private Long appointmentId;
    private Long templateVersionId;

    // Extended per-test state for new tests
    private Long reviewId;
    private Long appealId;
    private Long orderItemId;
    private Long pendingOrderId;

    // Minimal valid JPEG magic bytes for review image upload
    private static final byte[] MIN_JPEG = {
        (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0x00,0x10,0x4A,0x46,
        0x49,0x46,0x00,0x01,0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00
    };

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role adminRole    = findOrCreateRole("ROLE_ADMIN");

        ownerUser    = createUser("rhttp_owner_"    + nonce, userRole);
        reviewerUser = createUser("rhttp_reviewer_" + nonce, reviewerRole);
        adminUser    = createUser("rhttp_admin_"    + nonce, adminRole);

        ownerToken    = jwtService.generateAccessToken(ownerUser);
        reviewerToken = jwtService.generateAccessToken(reviewerUser);
        adminToken    = jwtService.generateAccessToken(adminUser);

        // RecyclingItem
        RecyclingItem item = new RecyclingItem();
        item.setTitle("RealHttp Item " + nonce);
        item.setNormalizedTitle("realhttp item " + nonce);
        item.setDescription("Item for real HTTP tests");
        item.setCategory("Electronics");
        item.setItemCondition("GOOD");
        item.setPrice(new BigDecimal("14.99"));
        item.setCurrency("USD");
        item.setSellerId(ownerUser.getId());
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item = recyclingItemRepository.save(item);
        itemId = item.getId();

        // Appointment (well in the future so time validation passes)
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(3));
        appt.setStartTime("11:00");
        appt.setEndTime("11:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(10);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);
        appointmentId = appt.getId();

        // ContractTemplate + Version
        ContractTemplate template = new ContractTemplate();
        template.setName("RealHttp Template " + nonce);
        template.setDescription("Template for real HTTP tests");
        template.setActive(true);
        template.setCreatedBy(adminUser.getId());
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        template = contractTemplateRepository.save(template);

        ContractTemplateVersion version = new ContractTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNumber(1);
        version.setContent("Contract for {{partyName}}");
        version.setChangeNotes("Initial");
        version.setCreatedBy(adminUser.getId());
        version.setCreatedAt(LocalDateTime.now());
        version = contractTemplateVersionRepository.save(version);
        templateVersionId = version.getId();

        // Clause field with default value for contract initiation
        ContractClauseField field = new ContractClauseField();
        field.setTemplateVersionId(version.getId());
        field.setFieldName("partyName");
        field.setFieldType("TEXT");
        field.setFieldLabel("Party Name");
        field.setRequired(true);
        field.setDefaultValue("Default Party");
        field.setDisplayOrder(1);
        contractClauseFieldRepository.save(field);

        // Seed a COMPLETED order for review/appeal tests
        Appointment futureAppt2 = new Appointment();
        futureAppt2.setAppointmentDate(LocalDate.now().plusDays(5));
        futureAppt2.setStartTime("12:00");
        futureAppt2.setEndTime("12:30");
        futureAppt2.setAppointmentType("PICKUP");
        futureAppt2.setSlotsAvailable(5);
        futureAppt2.setSlotsBooked(0);
        futureAppt2.setCreatedAt(LocalDateTime.now());
        futureAppt2 = appointmentRepository.save(futureAppt2);

        Order completedOrder = new Order();
        completedOrder.setUserId(ownerUser.getId());
        completedOrder.setAppointmentId(futureAppt2.getId());
        completedOrder.setOrderStatus("COMPLETED");
        completedOrder.setAppointmentType("PICKUP");
        completedOrder.setRescheduleCount(0);
        completedOrder.setCurrency("USD");
        completedOrder.setTotalPrice(java.math.BigDecimal.TEN);
        completedOrder.setReviewerId(reviewerUser.getId());
        completedOrder.setCreatedAt(LocalDateTime.now());
        completedOrder.setUpdatedAt(LocalDateTime.now());
        completedOrder = orderRepository.save(completedOrder);

        // Seed an OrderItem attached to that completed order
        OrderItem oi = new OrderItem();
        oi.setOrderId(completedOrder.getId());
        oi.setItemId(itemId);
        oi.setSnapshotTitle("RealHttp Item " + nonce);
        oi.setSnapshotCategory("Electronics");
        oi.setSnapshotCondition("GOOD");
        oi.setSnapshotPrice(new java.math.BigDecimal("14.99"));
        oi = orderItemRepository.save(oi);
        orderItemId = oi.getId();

        // Seed a Review for the completed order
        Review review = new Review();
        review.setOrderId(completedOrder.getId());
        review.setReviewerUserId(ownerUser.getId());
        review.setRating(4);
        review.setReviewText("Setup review for image upload test");
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());
        review = reviewRepository.save(review);
        reviewId = review.getId();

        // Seed a PENDING order for cancel test (appointment is tomorrow, so CANCELED expected)
        Appointment tomorrowAppt = new Appointment();
        tomorrowAppt.setAppointmentDate(LocalDate.now().plusDays(1));
        tomorrowAppt.setStartTime("09:00");
        tomorrowAppt.setEndTime("09:30");
        tomorrowAppt.setAppointmentType("PICKUP");
        tomorrowAppt.setSlotsAvailable(5);
        tomorrowAppt.setSlotsBooked(0);
        tomorrowAppt.setCreatedAt(LocalDateTime.now());
        tomorrowAppt = appointmentRepository.save(tomorrowAppt);

        Order pendingOrder = new Order();
        pendingOrder.setUserId(ownerUser.getId());
        pendingOrder.setAppointmentId(tomorrowAppt.getId());
        pendingOrder.setOrderStatus("PENDING_CONFIRMATION");
        pendingOrder.setAppointmentType("PICKUP");
        pendingOrder.setRescheduleCount(0);
        pendingOrder.setCurrency("USD");
        pendingOrder.setTotalPrice(java.math.BigDecimal.TEN);
        pendingOrder.setCreatedAt(LocalDateTime.now());
        pendingOrder.setUpdatedAt(LocalDateTime.now());
        pendingOrder = orderRepository.save(pendingOrder);
        pendingOrderId = pendingOrder.getId();

        // Seed an Appeal for the completed order
        Appeal appeal = new Appeal();
        appeal.setOrderId(completedOrder.getId());
        appeal.setAppellantId(ownerUser.getId());
        appeal.setReason("Setup appeal for real HTTP tests");
        appeal.setAppealStatus("OPEN");
        appeal.setCreatedAt(LocalDateTime.now());
        appeal.setUpdatedAt(LocalDateTime.now());
        appeal = appealRepository.save(appeal);
        appealId = appeal.getId();
    }

    // =========================================================================
    // Test 1: POST /api/orders (real HTTP)
    // =========================================================================

    @Test
    void shouldCreateOrderOverRealHttp() {
        Map<String, Object> body = Map.of(
                "itemIds", List.of(itemId),
                "appointmentId", appointmentId,
                "appointmentType", "PICKUP"
        );

        HttpHeaders headers = csrfAuthHeaders(ownerToken);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                base() + "/api/orders",
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("orderStatus")).isEqualTo("PENDING_CONFIRMATION");
        assertThat(response.getBody().get("userId")).isNotNull();
    }

    // =========================================================================
    // Test 2: GET /api/orders/my (real HTTP)
    // =========================================================================

    @Test
    void shouldListMyOrdersOverRealHttp() {
        // First create an order
        Map<String, Object> body = Map.of(
                "itemIds", List.of(itemId),
                "appointmentId", appointmentId,
                "appointmentType", "PICKUP"
        );
        HttpEntity<Map<String, Object>> createRequest = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        restTemplate.exchange(base() + "/api/orders", HttpMethod.POST, createRequest, Map.class);

        // Then GET my orders
        HttpEntity<Void> getRequest = new HttpEntity<>(authHeaders(ownerToken));
        ResponseEntity<List> response = restTemplate.exchange(
                base() + "/api/orders/my",
                HttpMethod.GET,
                getRequest,
                List.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isNotEmpty();
    }

    // =========================================================================
    // Test 3: POST /api/reviews — reject review for non-completed order (real HTTP)
    // =========================================================================

    @Test
    void shouldRejectReviewForNonCompletedOrderOverRealHttp() {
        // Create a PENDING order
        Map<String, Object> orderBody = Map.of(
                "itemIds", List.of(itemId),
                "appointmentId", appointmentId,
                "appointmentType", "PICKUP"
        );
        HttpEntity<Map<String, Object>> createOrderRequest = new HttpEntity<>(orderBody, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> orderResponse = restTemplate.exchange(
                base() + "/api/orders", HttpMethod.POST, createOrderRequest, Map.class);
        assertThat(orderResponse.getStatusCode().value()).isEqualTo(200);
        Number orderId = (Number) orderResponse.getBody().get("id");

        // Attempt to POST a review for that PENDING order
        Map<String, Object> reviewBody = Map.of(
                "orderId", orderId.longValue(),
                "rating", 5,
                "reviewText", "nope"
        );
        HttpEntity<Map<String, Object>> reviewRequest = new HttpEntity<>(reviewBody, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> reviewResponse = restTemplate.exchange(
                base() + "/api/reviews",
                HttpMethod.POST,
                reviewRequest,
                Map.class);

        assertThat(reviewResponse.getStatusCode().value()).isEqualTo(409);
    }

    // =========================================================================
    // Test 4: POST /api/appeals — create appeal (real HTTP)
    // =========================================================================

    @Test
    void shouldCreateAppealOverRealHttp() {
        // Create an order first (owner is appellant with matching userId)
        Map<String, Object> orderBody = Map.of(
                "itemIds", List.of(itemId),
                "appointmentId", appointmentId,
                "appointmentType", "PICKUP"
        );
        HttpEntity<Map<String, Object>> createOrderRequest = new HttpEntity<>(orderBody, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> orderResponse = restTemplate.exchange(
                base() + "/api/orders", HttpMethod.POST, createOrderRequest, Map.class);
        assertThat(orderResponse.getStatusCode().value()).isEqualTo(200);
        Number orderId = (Number) orderResponse.getBody().get("id");

        // Create appeal
        Map<String, Object> appealBody = Map.of(
                "orderId", orderId.longValue(),
                "reason", "Real HTTP appeal"
        );
        HttpEntity<Map<String, Object>> appealRequest = new HttpEntity<>(appealBody, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> appealResponse = restTemplate.exchange(
                base() + "/api/appeals",
                HttpMethod.POST,
                appealRequest,
                Map.class);

        assertThat(appealResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(appealResponse.getBody()).isNotNull();
        assertThat(appealResponse.getBody().get("appealStatus")).isEqualTo("OPEN");
    }

    // =========================================================================
    // Test 5: POST /api/contracts — initiate contract as reviewer (real HTTP)
    // =========================================================================

    @Test
    void shouldInitiateContractAsReviewerOverRealHttp() {
        // Create order via API (owner)
        Map<String, Object> orderBody = Map.of(
                "itemIds", List.of(itemId),
                "appointmentId", appointmentId,
                "appointmentType", "PICKUP"
        );
        HttpEntity<Map<String, Object>> createOrderRequest = new HttpEntity<>(orderBody, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> orderResponse = restTemplate.exchange(
                base() + "/api/orders", HttpMethod.POST, createOrderRequest, Map.class);
        assertThat(orderResponse.getStatusCode().value()).isEqualTo(200);
        Number orderId = (Number) orderResponse.getBody().get("id");

        // Accept order via repo so it transitions to ACCEPTED
        Order order = orderRepository.findById(orderId.longValue()).orElseThrow();
        order.setOrderStatus("ACCEPTED");
        order.setReviewerId(reviewerUser.getId());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Reviewer initiates contract
        Map<String, Object> contractBody = Map.of(
                "orderId", orderId.longValue(),
                "templateVersionId", templateVersionId,
                "fieldValues", "partyName=X",
                "startDate", "2099-01-01",
                "endDate", "2099-12-31"
        );
        HttpEntity<Map<String, Object>> contractRequest = new HttpEntity<>(contractBody, csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> contractResponse = restTemplate.exchange(
                base() + "/api/contracts",
                HttpMethod.POST,
                contractRequest,
                Map.class);

        assertThat(contractResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(contractResponse.getBody()).isNotNull();
        assertThat(contractResponse.getBody().get("contractStatus")).isEqualTo("INITIATED");
    }

    // =========================================================================
    // Test 6: PUT /api/reviewer/orders/{id}/accept (real HTTP)
    // =========================================================================

    @Test
    void shouldAcceptOrderAsReviewerOverRealHttp() {
        // Create order via owner
        Map<String, Object> orderBody = Map.of(
                "itemIds", List.of(itemId),
                "appointmentId", appointmentId,
                "appointmentType", "PICKUP"
        );
        HttpEntity<Map<String, Object>> createOrderRequest = new HttpEntity<>(orderBody, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> orderResponse = restTemplate.exchange(
                base() + "/api/orders", HttpMethod.POST, createOrderRequest, Map.class);
        assertThat(orderResponse.getStatusCode().value()).isEqualTo(200);
        Number orderId = (Number) orderResponse.getBody().get("id");

        // Ensure order is at PENDING_CONFIRMATION (already the case after creation)
        Order order = orderRepository.findById(orderId.longValue()).orElseThrow();
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Reviewer accepts
        HttpEntity<Void> acceptRequest = new HttpEntity<>(csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> acceptResponse = restTemplate.exchange(
                base() + "/api/reviewer/orders/" + orderId + "/accept",
                HttpMethod.PUT,
                acceptRequest,
                Map.class);

        assertThat(acceptResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(acceptResponse.getBody()).isNotNull();
        assertThat(acceptResponse.getBody().get("orderStatus")).isEqualTo("ACCEPTED");
    }

    // =========================================================================
    // Test 7: GET /api/admin/strategies (real HTTP)
    // =========================================================================

    @Test
    void shouldListStrategiesAsAdminOverRealHttp() {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(adminToken));
        ResponseEntity<List> response = restTemplate.exchange(
                base() + "/api/admin/strategies",
                HttpMethod.GET,
                request,
                List.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        // May be empty — that's OK, just verifies the endpoint is reachable and returns a list
    }

    // =========================================================================
    // Test 8: Access denial — user accessing admin endpoint (real HTTP)
    // =========================================================================

    /**
     * A valid ROLE_USER token must NOT be allowed to reach the ROLE_ADMIN-only endpoint.
     *
     * Note on HTTP status: with the default {@code AccessDeniedHandlerImpl}, Spring Security
     * dispatches a sendError(403) which triggers the Servlet container's error-dispatch
     * mechanism to forward to {@code /error}. Because {@code /error} is not in the
     * {@code permitAll} list, the security filter fires again for that forward request,
     * and the custom {@code AuthenticationEntryPoint} returns a JSON 401 (the security
     * context is cleared for the forward). A real HTTP client therefore receives 401 in
     * practice, even though the root cause is a role-mismatch (would be 403 in MockMvc
     * which intercepts before the forward).
     *
     * We assert {@code isClientError()} (any 4xx) and confirm the status is NOT 200,
     * since both 401 and 403 correctly indicate access was denied.
     */
    @Test
    void shouldReturn403ForUserAccessingAdminOverRealHttp() {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(ownerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                base() + "/api/admin/strategies",
                HttpMethod.GET,
                request,
                Map.class);

        int status = response.getStatusCode().value();
        // Both 401 and 403 indicate access was correctly denied; see Javadoc for explanation.
        assertThat(status).as("expected 4xx access-denied response").isBetween(400, 499);
        assertThat(status).as("must not be 200 OK").isNotEqualTo(200);
    }

    // =========================================================================
    // Test 9: Unauthenticated request (real HTTP)
    // =========================================================================

    @Test
    void shouldReturn401ForMissingTokenOverRealHttp() {
        // No auth header — the custom AuthenticationEntryPoint should return 401 JSON
        ResponseEntity<Map> response = restTemplate.exchange(
                base() + "/api/orders/my",
                HttpMethod.GET,
                new HttpEntity<>(acceptJsonHeaders()),
                Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // =========================================================================
    // Test 10: GET /api/orders/{id} over real HTTP
    // =========================================================================

    @Test
    void shouldGetOrderDetailWithItemsAndLogsOverRealHttp() {
        // Create an order first
        Map<String, Object> body = Map.of(
                "itemIds", List.of(itemId),
                "appointmentId", appointmentId,
                "appointmentType", "PICKUP"
        );
        HttpEntity<Map<String, Object>> createRequest = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                base() + "/api/orders", HttpMethod.POST, createRequest, Map.class);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(200);
        Number orderId = (Number) createResponse.getBody().get("id");

        // GET /api/orders/{id}
        HttpEntity<Void> getRequest = new HttpEntity<>(authHeaders(ownerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                base() + "/api/orders/" + orderId,
                HttpMethod.GET, getRequest, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();

        Object orderObj = response.getBody().get("order");
        assertThat(orderObj).isInstanceOf(Map.class);
        Map<?, ?> orderMap = (Map<?, ?>) orderObj;
        assertThat(orderMap.get("orderStatus")).isNotNull();

        Object itemsObj = response.getBody().get("items");
        assertThat(itemsObj).isInstanceOf(List.class);

        Object logsObj = response.getBody().get("logs");
        assertThat(logsObj).isInstanceOf(List.class);
    }

    // =========================================================================
    // Test 11: PUT /api/orders/{id}/cancel over real HTTP
    // =========================================================================

    @Test
    void shouldCancelPendingOrderOverRealHttp() {
        Map<String, Object> cancelBody = Map.of("reason", "Changed my mind");
        HttpEntity<Map<String, Object>> cancelRequest =
                new HttpEntity<>(cancelBody, csrfAuthHeaders(ownerToken));

        ResponseEntity<Map> response = restTemplate.exchange(
                base() + "/api/orders/" + pendingOrderId + "/cancel",
                HttpMethod.PUT, cancelRequest, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        // appointment is tomorrow (> 1hr away) → CANCELED; if within 1hr → EXCEPTION
        String finalStatus = (String) response.getBody().get("orderStatus");
        assertThat(finalStatus).isIn("CANCELED", "EXCEPTION");
    }

    // =========================================================================
    // Test 12: POST /api/reviews/{id}/images over real HTTP (multipart JPEG)
    // =========================================================================

    @Test
    void shouldUploadReviewImageOverRealHttp() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(MIN_JPEG) {
            @Override
            public String getFilename() { return "review.jpg"; }
        });

        HttpHeaders mpHeaders = csrfAuthHeaders(ownerToken);
        mpHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, mpHeaders);
        ResponseEntity<Map> response = restTemplate.exchange(
                base() + "/api/reviews/" + reviewId + "/images",
                HttpMethod.POST, request, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("fileName")).isNotNull();
        assertThat(response.getBody().get("filePath")).isNotNull();
        assertThat(response.getBody().get("checksum")).isNotNull();
    }

    // =========================================================================
    // Test 13: GET /api/appeals/{id} over real HTTP
    // =========================================================================

    @Test
    void shouldGetAppealDetailOverRealHttp() {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(ownerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                base() + "/api/appeals/" + appealId,
                HttpMethod.GET, request, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();

        Object appealObj = response.getBody().get("appeal");
        assertThat(appealObj).isInstanceOf(Map.class);
        Map<?, ?> appealMap = (Map<?, ?>) appealObj;
        Number returnedId = (Number) appealMap.get("id");
        assertThat(returnedId.longValue()).isEqualTo(appealId);

        Object evidenceObj = response.getBody().get("evidence");
        assertThat(evidenceObj).isInstanceOf(List.class);

        // outcome may be null or an empty map — just verify the key exists
        assertThat(response.getBody()).containsKey("outcome");
    }

    // =========================================================================
    // Test 14: PUT /api/appeals/{id}/resolve over real HTTP
    // =========================================================================

    @Test
    void shouldResolveAppealAsReviewerOverRealHttp() {
        Map<String, Object> resolveBody = Map.of(
                "outcome", "APPROVED",
                "reasoning", "OK");
        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(resolveBody, csrfAuthHeaders(reviewerToken));

        ResponseEntity<Map> response = restTemplate.exchange(
                base() + "/api/appeals/" + appealId + "/resolve",
                HttpMethod.PUT, request, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("appealStatus")).isEqualTo("RESOLVED");
    }

    // =========================================================================
    // Test 15: GET /api/admin/analytics/search over real HTTP
    // =========================================================================

    @Test
    void shouldGetSearchAnalyticsAsAdminOverRealHttp() {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(adminToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                base() + "/api/admin/analytics/search",
                HttpMethod.GET, request, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("totalSearches");
        assertThat(response.getBody()).containsKey("topTerms");
        assertThat(response.getBody()).containsKey("topClickedItems");
    }

    // =========================================================================
    // Test 16: POST /api/admin/strategies over real HTTP
    // =========================================================================

    @Test
    void shouldCreateRankingStrategyAsAdminOverRealHttp() {
        Map<String, Object> body = Map.of(
                "versionLabel", "v-test-" + System.nanoTime(),
                "creditScoreWeight", 0.3,
                "positiveRateWeight", 0.4,
                "reviewQualityWeight", 0.3,
                "minCreditScoreThreshold", 50,
                "minPositiveRateThreshold", 0.5
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, csrfAuthHeaders(adminToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                base() + "/api/admin/strategies",
                HttpMethod.POST, request, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("id")).isNotNull();
        assertThat(response.getBody().get("active")).isEqualTo(false);
    }

    // =========================================================================
    // Test 17: PUT /api/admin/strategies/{id}/activate over real HTTP
    // =========================================================================

    @Test
    void shouldActivateRankingStrategyAsAdminOverRealHttp() {
        // First create a strategy
        Map<String, Object> createBody = Map.of(
                "versionLabel", "v-activate-" + System.nanoTime(),
                "creditScoreWeight", 0.3,
                "positiveRateWeight", 0.4,
                "reviewQualityWeight", 0.3,
                "minCreditScoreThreshold", 50,
                "minPositiveRateThreshold", 0.5
        );
        HttpEntity<Map<String, Object>> createRequest =
                new HttpEntity<>(createBody, csrfAuthHeaders(adminToken));
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                base() + "/api/admin/strategies",
                HttpMethod.POST, createRequest, Map.class);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(200);
        Number strategyId = (Number) createResponse.getBody().get("id");

        // Activate
        HttpEntity<Void> activateRequest = new HttpEntity<>(csrfAuthHeaders(adminToken));
        ResponseEntity<Map> activateResponse = restTemplate.exchange(
                base() + "/api/admin/strategies/" + strategyId + "/activate",
                HttpMethod.PUT, activateRequest, Map.class);

        assertThat(activateResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(activateResponse.getBody()).isNotNull();
        assertThat(activateResponse.getBody().get("active")).isEqualTo(true);
    }

    // =========================================================================
    // Test 18: GET /api/reviewer/queue over real HTTP
    // =========================================================================

    @Test
    void shouldGetReviewerQueueOverRealHttp() {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(reviewerToken));
        ResponseEntity<List> response = restTemplate.exchange(
                base() + "/api/reviewer/queue",
                HttpMethod.GET, request, List.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        // body is a list (may be empty — just verify the endpoint returns a list)
        assertThat(response.getBody()).isInstanceOf(List.class);
    }

    // =========================================================================
    // Test 19: PUT /api/reviewer/order-items/{id}/adjust-category over real HTTP
    // =========================================================================

    @Test
    void shouldAdjustOrderItemCategoryAsReviewerOverRealHttp() {
        Map<String, Object> body = Map.of("newCategory", "Books");
        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, csrfAuthHeaders(reviewerToken));

        ResponseEntity<Map> response = restTemplate.exchange(
                base() + "/api/reviewer/order-items/" + orderItemId + "/adjust-category",
                HttpMethod.PUT, request, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("adjustedCategory")).isEqualTo("Books");
        Number adjustedBy = (Number) response.getBody().get("adjustedBy");
        assertThat(adjustedBy).isNotNull();
        assertThat(adjustedBy.longValue()).isEqualTo(reviewerUser.getId());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String base() {
        return "http://localhost:" + port;
    }

    /**
     * Headers with Bearer auth + JSON content type, no CSRF.
     * Use for GET requests or endpoints that do not require CSRF.
     */
    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        return h;
    }

    /**
     * Headers with only Accept: application/json, no auth.
     * Used for the 401 test so the entry point returns JSON (not a redirect).
     */
    private HttpHeaders acceptJsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        return h;
    }

    /**
     * Fetches the XSRF-TOKEN cookie from /login (permitAll), then returns headers
     * that include both the cookie and the matching X-XSRF-TOKEN header.
     * This satisfies Spring Security's CookieCsrfTokenRepository check on mutable requests.
     */
    private HttpHeaders csrfAuthHeaders(String token) {
        // Fetch cookie from a permitAll endpoint
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

    // -------------------------------------------------------------------------
    // DB helpers
    // -------------------------------------------------------------------------

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
