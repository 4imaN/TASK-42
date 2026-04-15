package com.reclaim.portal.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.reclaim.portal.contracts.service.ContractService;
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
 * Real-HTTP multi-step business journey tests using TestRestTemplate (no MockMvc).
 * Replaces / supplements the MockMvc-based UserJourneyE2ETest with true real-HTTP coverage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpUserJourneyTest {

    // Minimal valid PNG bytes (1×1 pixel)
    private static final byte[] MINIMAL_PNG = {
        (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01
    };

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private RecyclingItemRepository recyclingItemRepository;
    @Autowired private ContractTemplateRepository contractTemplateRepository;
    @Autowired private ContractTemplateVersionRepository contractTemplateVersionRepository;
    @Autowired private ContractClauseFieldRepository contractClauseFieldRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ContractService contractService;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // Shared per-test state set up in @BeforeEach
    private User user;
    private User reviewer;
    private User admin;
    private String userToken;
    private String reviewerToken;
    private String adminToken;
    private Long itemId;
    private String seededTitle;
    private Long appointmentId;
    private Long templateVersionId;

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role adminRole    = findOrCreateRole("ROLE_ADMIN");

        user     = createUser("rhjt_user_"     + nonce, userRole);
        reviewer = createUser("rhjt_reviewer_" + nonce, reviewerRole);
        admin    = createUser("rhjt_admin_"    + nonce, adminRole);

        userToken     = jwtService.generateAccessToken(user);
        reviewerToken = jwtService.generateAccessToken(reviewer);
        adminToken    = jwtService.generateAccessToken(admin);

        // RecyclingItem
        seededTitle = "RealHttpJourney_" + nonce;
        RecyclingItem item = new RecyclingItem();
        item.setTitle(seededTitle);
        item.setNormalizedTitle(seededTitle.toLowerCase());
        item.setDescription("Journey item for real HTTP tests");
        item.setCategory("Electronics");
        item.setItemCondition("GOOD");
        item.setPrice(new BigDecimal("29.99"));
        item.setCurrency("USD");
        item.setSellerId(user.getId());
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item = recyclingItemRepository.save(item);
        itemId = item.getId();

        // Appointment — well in the future so time validation passes
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

        // ContractTemplate + Version + ClauseField with defaultValue so initiateContract
        // succeeds even when fieldValues is null / empty
        ContractTemplate template = new ContractTemplate();
        template.setName("RealHttpJourney Template " + nonce);
        template.setDescription("Template for real HTTP journey tests");
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
        field.setDefaultValue("Default Party");  // ensures initiateContract works with no explicit value
        field.setDisplayOrder(1);
        contractClauseFieldRepository.save(field);
    }

    // =========================================================================
    // Journey 1: Login → catalog → click → order → reviewer accept → complete → review
    // =========================================================================

    @Test
    void userFromLoginToReviewJourneyOverRealHttp() {
        // ---- Step 1: Login ----
        Map<String, String> loginBody = Map.of(
                "username", user.getUsername(),
                "password", "TestPassword1!");

        HttpEntity<Map<String, String>> loginRequest =
                new HttpEntity<>(loginBody, csrfAuthHeaders(null));
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                base() + "/api/auth/login", HttpMethod.POST, loginRequest, Map.class);

        assertThat(loginResponse.getStatusCode().value()).isEqualTo(200);
        String accessToken = (String) loginResponse.getBody().get("accessToken");
        assertThat(accessToken).isNotBlank();

        // ---- Step 2: List catalog ----
        HttpEntity<Void> searchRequest = new HttpEntity<>(authHeaders(accessToken));
        ResponseEntity<Map> searchResponse = restTemplate.exchange(
                base() + "/api/catalog/search?keyword=" + seededTitle,
                HttpMethod.GET, searchRequest, Map.class);

        assertThat(searchResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(searchResponse.getBody()).isNotNull();
        List<?> items = (List<?>) searchResponse.getBody().get("items");
        assertThat(items).isNotNull();
        assertThat(items).isNotEmpty();

        // Find the seeded item in results
        boolean foundItem = items.stream()
                .filter(i -> i instanceof Map)
                .map(i -> (Map<?, ?>) i)
                .anyMatch(i -> seededTitle.equals(i.get("title")));
        assertThat(foundItem).as("catalog search should return seeded item by title").isTrue();

        // ---- Step 3: Log click ----
        Map<String, Object> clickBody = Map.of("itemId", itemId);
        HttpEntity<Map<String, Object>> clickRequest =
                new HttpEntity<>(clickBody, csrfAuthHeaders(accessToken));
        ResponseEntity<Map> clickResponse = restTemplate.exchange(
                base() + "/api/catalog/click", HttpMethod.POST, clickRequest, Map.class);
        assertThat(clickResponse.getStatusCode().value()).isEqualTo(200);

        // ---- Step 4: Create order ----
        Map<String, Object> orderBody = Map.of(
                "itemIds", List.of(itemId),
                "appointmentId", appointmentId,
                "appointmentType", "PICKUP");
        HttpEntity<Map<String, Object>> orderRequest =
                new HttpEntity<>(orderBody, csrfAuthHeaders(accessToken));
        ResponseEntity<Map> orderResponse = restTemplate.exchange(
                base() + "/api/orders", HttpMethod.POST, orderRequest, Map.class);

        assertThat(orderResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(orderResponse.getBody()).isNotNull();
        assertThat(orderResponse.getBody().get("orderStatus")).isEqualTo("PENDING_CONFIRMATION");
        Number orderId = (Number) orderResponse.getBody().get("id");
        assertThat(orderId).isNotNull();

        // ---- Step 5: Reviewer accept ----
        HttpEntity<Void> acceptRequest = new HttpEntity<>(csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> acceptResponse = restTemplate.exchange(
                base() + "/api/reviewer/orders/" + orderId + "/accept",
                HttpMethod.PUT, acceptRequest, Map.class);

        assertThat(acceptResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(acceptResponse.getBody().get("orderStatus")).isEqualTo("ACCEPTED");

        // ---- Step 6: Reviewer complete ----
        HttpEntity<Void> completeRequest = new HttpEntity<>(csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> completeResponse = restTemplate.exchange(
                base() + "/api/orders/" + orderId + "/complete",
                HttpMethod.PUT, completeRequest, Map.class);

        assertThat(completeResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(completeResponse.getBody().get("orderStatus")).isEqualTo("COMPLETED");

        // ---- Step 7: User creates review ----
        Map<String, Object> reviewBody = Map.of(
                "orderId", orderId.longValue(),
                "rating", 5,
                "reviewText", "Great service!");
        HttpEntity<Map<String, Object>> reviewRequest =
                new HttpEntity<>(reviewBody, csrfAuthHeaders(accessToken));
        ResponseEntity<Map> reviewResponse = restTemplate.exchange(
                base() + "/api/reviews", HttpMethod.POST, reviewRequest, Map.class);

        assertThat(reviewResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(reviewResponse.getBody()).isNotNull();
        assertThat(reviewResponse.getBody().get("id")).isNotNull();
        assertThat(reviewResponse.getBody().get("rating")).isEqualTo(5);
        assertThat(reviewResponse.getBody().get("reviewText")).isEqualTo("Great service!");

        // ---- Step 8: User lists own reviews ----
        HttpEntity<Void> myReviewsRequest = new HttpEntity<>(authHeaders(accessToken));
        ResponseEntity<List> myReviewsResponse = restTemplate.exchange(
                base() + "/api/reviews/my", HttpMethod.GET, myReviewsRequest, List.class);

        assertThat(myReviewsResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(myReviewsResponse.getBody()).isNotNull();
        assertThat(myReviewsResponse.getBody().size()).isGreaterThanOrEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Object> reviewList = (List<Object>) myReviewsResponse.getBody();
        boolean foundReview = reviewList.stream()
                .filter(r -> r instanceof Map)
                .map(r -> (Map<String, Object>) r)
                .anyMatch(r -> {
                    Object oid = r.get("orderId");
                    return oid != null && orderId.longValue() == ((Number) oid).longValue();
                });
        assertThat(foundReview)
                .as("my reviews should contain the review we just created for orderId=%d", orderId.longValue())
                .isTrue();
    }

    // =========================================================================
    // Journey 2: Contract initiation, confirm, sign, list, archive
    // =========================================================================

    @Test
    void contractInitiationAndSignJourneyOverRealHttp() {
        // ---- Step 1: Create an order in ACCEPTED state via repo ----
        Order order = new Order();
        order.setUserId(user.getId());
        order.setAppointmentId(appointmentId);
        order.setOrderStatus("ACCEPTED");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(BigDecimal.TEN);
        order.setReviewerId(reviewer.getId());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        final long orderId = order.getId();

        // ---- Step 2: Reviewer initiates contract ----
        Map<String, Object> contractBody = new LinkedHashMap<>();
        contractBody.put("orderId", orderId);
        contractBody.put("templateVersionId", templateVersionId);
        contractBody.put("fieldValues", "partyName=John");
        contractBody.put("startDate", LocalDate.now().toString());
        contractBody.put("endDate", LocalDate.now().plusDays(365).toString());

        HttpEntity<Map<String, Object>> contractRequest =
                new HttpEntity<>(contractBody, csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> initiateResponse = restTemplate.exchange(
                base() + "/api/contracts", HttpMethod.POST, contractRequest, Map.class);

        assertThat(initiateResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(initiateResponse.getBody()).isNotNull();
        assertThat(initiateResponse.getBody().get("contractStatus")).isEqualTo("INITIATED");
        Number contractId = (Number) initiateResponse.getBody().get("id");
        assertThat(contractId).isNotNull();

        // ---- Step 3: Confirm ----
        HttpEntity<Void> confirmRequest = new HttpEntity<>(csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map> confirmResponse = restTemplate.exchange(
                base() + "/api/contracts/" + contractId + "/confirm",
                HttpMethod.PUT, confirmRequest, Map.class);

        assertThat(confirmResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(confirmResponse.getBody().get("contractStatus")).isEqualTo("CONFIRMED");

        // ---- Step 4: Sign — owner PUT with multipart PNG ----
        MultiValueMap<String, Object> signBody = new LinkedMultiValueMap<>();
        signBody.add("file", new ByteArrayResource(MINIMAL_PNG) {
            @Override
            public String getFilename() { return "sig.png"; }
        });
        HttpHeaders mpHeaders = csrfAuthHeaders(userToken);
        mpHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> signRequest = new HttpEntity<>(signBody, mpHeaders);
        ResponseEntity<Map> signResponse = restTemplate.exchange(
                base() + "/api/contracts/" + contractId + "/sign?signatureType=DRAWN",
                HttpMethod.PUT, signRequest, Map.class);

        assertThat(signResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(signResponse.getBody()).isNotNull();
        assertThat(signResponse.getBody().get("contractStatus")).isEqualTo("SIGNED");
        assertThat(signResponse.getBody().get("signedAt")).isNotNull();

        // ---- Step 5: Owner lists contracts ----
        HttpEntity<Void> myContractsRequest = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<List> myContractsResponse = restTemplate.exchange(
                base() + "/api/contracts/my", HttpMethod.GET, myContractsRequest, List.class);

        assertThat(myContractsResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(myContractsResponse.getBody()).isNotNull();
        assertThat(myContractsResponse.getBody().size()).isGreaterThanOrEqualTo(1);

        // ---- Step 6: Admin archives ----
        HttpEntity<Void> archiveRequest = new HttpEntity<>(csrfAuthHeaders(adminToken));
        ResponseEntity<Map> archiveResponse = restTemplate.exchange(
                base() + "/api/contracts/" + contractId + "/archive",
                HttpMethod.PUT, archiveRequest, Map.class);

        assertThat(archiveResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(archiveResponse.getBody().get("contractStatus")).isEqualTo("ARCHIVED");
    }

    // =========================================================================
    // Helpers — identical pattern to RealHttpApiTest
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
