package com.reclaim.portal.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.contracts.entity.ContractTemplate;
import com.reclaim.portal.contracts.entity.ContractTemplateVersion;
import com.reclaim.portal.contracts.repository.ContractTemplateRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateVersionRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that POST /api/contracts (contract initiation) is restricted to
 * REVIEWER and ADMIN roles; regular USERs must receive 403 Forbidden.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContractInitiationAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ContractTemplateRepository templateRepository;

    @Autowired
    private ContractTemplateVersionRepository templateVersionRepository;

    private String userToken;
    private String reviewerToken;

    private Long orderId;
    private Long templateVersionId;
    private Long reviewerId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role adminRole = findOrCreateRole("ROLE_ADMIN");

        // Create a regular user
        User user = createUser("contract_auth_user_" + nonce, userRole);
        userToken = jwtService.generateAccessToken(user);

        // Create a reviewer
        User reviewer = createUser("contract_auth_reviewer_" + nonce, reviewerRole);
        reviewerToken = jwtService.generateAccessToken(reviewer);
        reviewerId = reviewer.getId();

        // Create an admin (needed for template creation)
        User admin = createUser("contract_auth_admin_" + nonce, adminRole);

        // Create appointment
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(LocalDate.now().plusDays(5));
        appointment.setStartTime("09:00");
        appointment.setEndTime("09:30");
        appointment.setAppointmentType("PICKUP");
        appointment.setSlotsAvailable(5);
        appointment.setSlotsBooked(1);
        appointment.setCreatedAt(LocalDateTime.now());
        appointment = appointmentRepository.save(appointment);

        // Create an order owned by the regular user
        Order order = new Order();
        order.setUserId(user.getId());
        order.setAppointmentId(appointment.getId());
        order.setOrderStatus("ACCEPTED");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("20.00"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        orderId = order.getId();

        // Create a contract template and version
        ContractTemplate template = new ContractTemplate();
        template.setName("Auth Test Template " + nonce);
        template.setDescription("Template for auth testing");
        template.setActive(true);
        template.setCreatedBy(admin.getId());
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        template = templateRepository.save(template);

        ContractTemplateVersion version = new ContractTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNumber(1);
        version.setContent("Contract for order ${orderId}");
        version.setChangeNotes("Initial version");
        version.setCreatedBy(admin.getId());
        version.setCreatedAt(LocalDateTime.now());
        version = templateVersionRepository.save(version);
        templateVersionId = version.getId();
    }

    @Test
    void shouldDenyRegularUserContractInitiation() throws Exception {
        Map<String, Object> body = buildInitiateBody();

        mockMvc.perform(post("/api/contracts")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
               .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowReviewerContractInitiation() throws Exception {
        Map<String, Object> body = buildInitiateBody();

        mockMvc.perform(post("/api/contracts")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
               .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> buildInitiateBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("templateVersionId", templateVersionId);
        body.put("fieldValues", "{}");
        body.put("startDate", LocalDate.now().plusDays(1).toString());
        body.put("endDate", LocalDate.now().plusDays(365).toString());
        return body;
    }

    private Role findOrCreateRole(String roleName) {
        return roleRepository.findByName(roleName).orElseGet(() -> {
            Role r = new Role();
            r.setName(roleName);
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
    }

    private User createUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        user.setEmail(username + "@example.com");
        user.setEnabled(true);
        user.setLocked(false);
        user.setForcePasswordReset(false);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>(Set.of(role)));
        return userRepository.save(user);
    }
}
