package com.reclaim.portal.e2e;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.contracts.entity.ContractClauseField;
import com.reclaim.portal.contracts.entity.ContractInstance;
import com.reclaim.portal.contracts.entity.ContractTemplate;
import com.reclaim.portal.contracts.entity.ContractTemplateVersion;
import com.reclaim.portal.contracts.repository.ContractClauseFieldRepository;
import com.reclaim.portal.contracts.repository.ContractInstanceRepository;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-HTTP coverage for contract lifecycle transitions:
 * confirm, renew, terminate, void — plus access control tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpContractLifecycleTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ContractTemplateRepository contractTemplateRepository;
    @Autowired private ContractTemplateVersionRepository contractTemplateVersionRepository;
    @Autowired private ContractClauseFieldRepository contractClauseFieldRepository;
    @Autowired private ContractInstanceRepository contractInstanceRepository;
    @Autowired private ContractService contractService;
    @Autowired private JwtService jwtService;

    private User owner;
    private User reviewer;
    private User admin;
    private String ownerToken;
    private String reviewerToken;
    private String adminToken;
    private Long templateVersionId;
    private Long acceptedOrderId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role adminRole    = findOrCreateRole("ROLE_ADMIN");

        owner    = createUser("cl_owner_"    + nonce, userRole);
        reviewer = createUser("cl_reviewer_" + nonce, reviewerRole);
        admin    = createUser("cl_admin_"    + nonce, adminRole);

        ownerToken    = jwtService.generateAccessToken(owner);
        reviewerToken = jwtService.generateAccessToken(reviewer);
        adminToken    = jwtService.generateAccessToken(admin);

        // Appointment
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(10));
        appt.setStartTime("14:00");
        appt.setEndTime("14:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(10);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        // ACCEPTED order (the owner of this order drives contract.userId)
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
        acceptedOrderId = order.getId();

        // ContractTemplate + Version + ClauseField with default value
        ContractTemplate template = new ContractTemplate();
        template.setName("LC Template " + nonce);
        template.setDescription("Lifecycle test template");
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

    /**
     * Helper: creates a fresh ACCEPTED order so each test gets its own contract.
     */
    private ContractInstance createInitiatedContract() {
        // Each test needs a fresh order (one contract per order)
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(11));
        appt.setStartTime("15:00");
        appt.setEndTime("15:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(5);
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

        return contractService.initiateContract(
                order.getId(), templateVersionId, reviewer.getId(), reviewer.getId(),
                "partyName=TestParty",
                LocalDate.now(),
                LocalDate.now().plusYears(1));
    }

    // =========================================================================
    // 1. Confirm initiated contract
    // =========================================================================

    @Test
    void shouldConfirmInitiatedContractOverRealHttp() {
        ContractInstance contract = createInitiatedContract();

        HttpEntity<Void> req = new HttpEntity<>(csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/contracts/" + contract.getId() + "/confirm",
                HttpMethod.PUT, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("contractStatus")).isEqualTo("CONFIRMED");
    }

    // =========================================================================
    // 2. Confirm already-CONFIRMED contract → 409
    // =========================================================================

    @Test
    void shouldRejectConfirmForWrongStatusOverRealHttp() {
        ContractInstance contract = createInitiatedContract();
        // Confirm once via service
        contractService.confirmContract(contract.getId(), reviewer.getId(), true);

        // Second confirm attempt via HTTP should fail
        HttpEntity<Void> req = new HttpEntity<>(csrfAuthHeaders(reviewerToken));
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/contracts/" + contract.getId() + "/confirm",
                HttpMethod.PUT, req, String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(409);
    }

    // =========================================================================
    // 3. Renew a SIGNED contract
    // =========================================================================

    @Test
    void shouldRenewSignedContractOverRealHttp() {
        ContractInstance contract = createInitiatedContract();
        // Set status to SIGNED via repo (skip the file-upload sign step)
        contract.setContractStatus("SIGNED");
        contract.setSignedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contractInstanceRepository.save(contract);

        Map<String, String> body = Map.of("newEndDate", "2099-12-31");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/contracts/" + contract.getId() + "/renew",
                HttpMethod.PUT, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("contractStatus")).isEqualTo("RENEWED");
        assertThat(resp.getBody().get("endDate")).isEqualTo("2099-12-31");
    }

    // =========================================================================
    // 4. Renew INITIATED contract → 409 (must be SIGNED or ACTIVE)
    // =========================================================================

    @Test
    void shouldRejectRenewUnsignedContractOverRealHttp() {
        ContractInstance contract = createInitiatedContract();

        Map<String, String> body = Map.of("newEndDate", "2099-12-31");
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, csrfAuthHeaders(ownerToken));
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/contracts/" + contract.getId() + "/renew",
                HttpMethod.PUT, req, String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(409);
    }

    // =========================================================================
    // 5. Reviewer can terminate a SIGNED contract
    // =========================================================================

    @Test
    void shouldTerminateAsReviewerOverRealHttp() {
        ContractInstance contract = createInitiatedContract();
        contract.setContractStatus("SIGNED");
        contract.setSignedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contractInstanceRepository.save(contract);

        HttpEntity<Void> req = new HttpEntity<>(csrfAuthHeaders(reviewerToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/contracts/" + contract.getId() + "/terminate",
                HttpMethod.PUT, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("contractStatus")).isEqualTo("TERMINATED");
    }

    // =========================================================================
    // 6. Admin can void an INITIATED contract
    // =========================================================================

    @Test
    void shouldVoidAsAdminOverRealHttp() {
        ContractInstance contract = createInitiatedContract();

        HttpEntity<Void> req = new HttpEntity<>(csrfAuthHeaders(adminToken));
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base() + "/api/contracts/" + contract.getId() + "/void",
                HttpMethod.PUT, req,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("contractStatus")).isEqualTo("VOIDED");
    }

    // =========================================================================
    // 7. Regular user cannot void → 403
    // =========================================================================

    @Test
    void shouldRejectVoidAsRegularUserOverRealHttp() {
        ContractInstance contract = createInitiatedContract();

        HttpEntity<Void> req = new HttpEntity<>(csrfAuthHeaders(ownerToken));
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/contracts/" + contract.getId() + "/void",
                HttpMethod.PUT, req, String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
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
