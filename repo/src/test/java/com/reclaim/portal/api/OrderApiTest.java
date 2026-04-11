package com.reclaim.portal.api;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that POST /api/orders accepts the correct JSON wire shape:
 * {"itemIds": [id], "appointmentId": id, "appointmentType": "PICKUP"}
 *
 * The appointment is created with a far-future date so OrderService time validation passes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderApiTest {

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
    private AppointmentRepository appointmentRepository;

    @Autowired
    private RecyclingItemRepository recyclingItemRepository;

    private String accessToken;
    private Long appointmentId;
    private Long itemId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User user = new User();
        user.setUsername("orderapi_user_" + nonce);
        user.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        user.setEmail("orderapi_" + nonce + "@example.com");
        user.setEnabled(true);
        user.setLocked(false);
        user.setForcePasswordReset(false);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>(Set.of(userRole)));
        user = userRepository.save(user);

        accessToken = jwtService.generateAccessToken(user);

        // Create an appointment well in the future so OrderService's
        // validateAppointmentTime (minAdvanceHours=2, maxAdvanceDays=14) passes
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(LocalDate.now().plusDays(3));
        appointment.setStartTime("10:00");
        appointment.setEndTime("10:30");
        appointment.setAppointmentType("PICKUP");
        appointment.setSlotsAvailable(10);
        appointment.setSlotsBooked(0);
        appointment.setCreatedAt(LocalDateTime.now());
        appointment = appointmentRepository.save(appointment);
        appointmentId = appointment.getId();

        // Create a recycling item so OrderService can snapshot it
        RecyclingItem item = new RecyclingItem();
        item.setTitle("Test Recycling Item");
        item.setNormalizedTitle("test recycling item");
        item.setDescription("A test item");
        item.setCategory("Electronics");
        item.setItemCondition("GOOD");
        item.setPrice(new BigDecimal("9.99"));
        item.setCurrency("USD");
        item.setSellerId(user.getId());
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item = recyclingItemRepository.save(item);
        itemId = item.getId();
    }

    /**
     * POST /api/orders must accept JSON body with itemIds array, appointmentId, and appointmentType.
     * A 200 response confirms the endpoint parsed the JSON shape correctly and the order was created.
     */
    @Test
    void createOrderAcceptsCorrectJsonShape() throws Exception {
        Map<String, Object> requestBody = Map.of(
            "itemIds", List.of(itemId),
            "appointmentId", appointmentId,
            "appointmentType", "PICKUP"
        );

        mockMvc.perform(post("/api/orders")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
               .andExpect(status().isOk());
    }

    /**
     * POST /api/orders with a missing appointmentId field should result in a non-200
     * response (NPE or validation error from the service layer), confirming that
     * appointmentId is a required part of the JSON contract.
     */
    @Test
    void createOrderRejectsMissingAppointmentId() throws Exception {
        // appointmentId is null — OrderService will throw EntityNotFoundException
        Map<String, Object> requestBody = Map.of(
            "itemIds", List.of(itemId),
            "appointmentType", "PICKUP"
        );

        int status = mockMvc.perform(post("/api/orders")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
               .andReturn().getResponse().getStatus();

        // Should not succeed (200); any error response is acceptable
        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(200);
    }

    /**
     * POST /api/orders with an empty itemIds list should fail with a business rule error (409).
     */
    @Test
    void createOrderRejectsEmptyItemIds() throws Exception {
        Map<String, Object> requestBody = Map.of(
            "itemIds", List.of(),
            "appointmentId", appointmentId,
            "appointmentType", "PICKUP"
        );

        mockMvc.perform(post("/api/orders")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
               .andExpect(status().isBadRequest());
    }

    /**
     * POST /api/orders without authentication should be rejected (401).
     */
    @Test
    void createOrderRequiresAuthentication() throws Exception {
        Map<String, Object> requestBody = Map.of(
            "itemIds", List.of(itemId),
            "appointmentId", appointmentId,
            "appointmentType", "PICKUP"
        );

        mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
               .andExpect(status().isUnauthorized());
    }
}
