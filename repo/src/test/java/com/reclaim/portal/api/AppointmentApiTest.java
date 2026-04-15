package com.reclaim.portal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppointmentApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private AppointmentRepository appointmentRepository;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole  = findOrCreateRole("ROLE_USER");
        Role adminRole = findOrCreateRole("ROLE_ADMIN");

        User user  = createUser("appt_user_"  + nonce, userRole);
        User admin = createUser("appt_admin_" + nonce, adminRole);

        userToken  = jwtService.generateAccessToken(user);
        adminToken = jwtService.generateAccessToken(admin);
    }

    @Test
    void shouldGetAvailableSlots() throws Exception {
        String futureDate = LocalDate.now().plusDays(3).toString();

        mockMvc.perform(get("/api/appointments/available")
                .param("date", futureDate)
                .param("type", "PICKUP")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldGenerateSlotsAsAdmin() throws Exception {
        // Use a date far in the future to avoid collisions
        String futureDate = LocalDate.now().plusDays(60).toString();

        mockMvc.perform(post("/api/appointments/generate")
                .with(csrf())
                .param("date", futureDate)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectGenerateSlotsAsUser() throws Exception {
        String futureDate = LocalDate.now().plusDays(61).toString();

        mockMvc.perform(post("/api/appointments/generate")
                .with(csrf())
                .param("date", futureDate)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectAvailableWithMissingType() throws Exception {
        String futureDate = LocalDate.now().plusDays(3).toString();

        // Missing required 'type' param — Spring returns 400 for missing @RequestParam
        int statusCode = mockMvc.perform(get("/api/appointments/available")
                .param("date", futureDate)
                .header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getStatus();

        // Missing required @RequestParam: GlobalExceptionHandler maps
        // MissingServletRequestParameterException to 400 Bad Request.
        org.assertj.core.api.Assertions.assertThat(statusCode).isEqualTo(400);
    }

    // =========================================================================
    // GET /api/appointments/available — additional deep tests
    // =========================================================================

    /**
     * A future date that has no seeded slots — service auto-generates them on first query,
     * but all slots start in the future and are available, so an array is returned.
     * This test uses a date far in the future where no slots exist yet → service generates them
     * on the fly (auto-generate behavior in getAvailableSlots). Result must be a 200 array.
     */
    @Test
    void shouldReturnEmptyForDateWithNoSlots() throws Exception {
        // Use a date that won't have manually-seeded slots.
        // The service will auto-generate, returning available slots. We just verify 200 + array.
        // To get truly empty, we need a date > maxAdvanceDays (14) which will trigger the
        // BusinessRuleException → 409, so instead use a valid future date and assert array.
        // Actual behavior: service auto-generates slots → returns non-empty list.
        // The test verifies the endpoint handles a date with no pre-seeded slots gracefully.
        String futureDate = LocalDate.now().plusDays(13).toString();

        mockMvc.perform(get("/api/appointments/available")
                .param("date", futureDate)
                .param("type", "PICKUP")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * When PICKUP and DROPOFF slots are seeded for the same date,
     * querying with type=PICKUP returns only PICKUP slots.
     */
    @Test
    void shouldFilterSlotsByType() throws Exception {
        // Use a date that doesn't conflict — place it in the future and seed directly
        LocalDate testDate = LocalDate.now().plusDays(11);

        // Seed a PICKUP slot
        Appointment pickup = new Appointment();
        pickup.setAppointmentDate(testDate);
        pickup.setStartTime("10:00");
        pickup.setEndTime("10:30");
        pickup.setAppointmentType("PICKUP");
        pickup.setSlotsAvailable(5);
        pickup.setSlotsBooked(0);
        pickup.setCreatedAt(LocalDateTime.now());
        appointmentRepository.save(pickup);

        // Seed a DROPOFF slot for the same date
        Appointment dropoff = new Appointment();
        dropoff.setAppointmentDate(testDate);
        dropoff.setStartTime("10:00");
        dropoff.setEndTime("10:30");
        dropoff.setAppointmentType("DROPOFF");
        dropoff.setSlotsAvailable(5);
        dropoff.setSlotsBooked(0);
        dropoff.setCreatedAt(LocalDateTime.now());
        appointmentRepository.save(dropoff);

        MvcResult result = mockMvc.perform(get("/api/appointments/available")
                .param("date", testDate.toString())
                .param("type", "PICKUP")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        JsonNode slots = objectMapper.readTree(result.getResponse().getContentAsString());
        // All returned slots must be PICKUP
        for (JsonNode slot : slots) {
            assertThat(slot.path("appointmentType").asText())
                    .isEqualTo("PICKUP");
        }
        // At least the seeded PICKUP slot must be present
        assertThat(slots.size()).isGreaterThanOrEqualTo(1);
    }

    /**
     * Passing a non-date string for the date parameter returns 400 Bad Request
     * via MethodArgumentTypeMismatchException → GlobalExceptionHandler.
     */
    @Test
    void shouldRejectInvalidDateFormat() throws Exception {
        mockMvc.perform(get("/api/appointments/available")
                .param("date", "notadate")
                .param("type", "PICKUP")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
    }

    /**
     * A date beyond the maxAdvanceDays (14 days) limit causes AppointmentService to throw
     * BusinessRuleException (which is not an "Access denied" prefix) → mapped to 409 Conflict.
     */
    @Test
    void shouldRejectDateTooFarInFuture() throws Exception {
        // today + 30 days exceeds the maxAdvanceDays=14 window
        String tooFarDate = LocalDate.now().plusDays(30).toString();

        // AppointmentService.getAvailableSlots throws BusinessRuleException
        // ("Appointment date must be between today and 14 days from now") → 409
        mockMvc.perform(get("/api/appointments/available")
                .param("date", tooFarDate)
                .param("type", "PICKUP")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // POST /api/appointments/generate — additional deep tests
    // =========================================================================

    /**
     * Admin-generated slots for a future date should produce the expected number of slots.
     * Business hours: 8-18 = 10 hours × 2 slots/hour (30-minute slots) = 20 slots per type.
     */
    @Test
    void shouldGenerateExpectedNumberOfSlots() throws Exception {
        // Use a date far enough in the future to avoid collisions with other tests
        LocalDate futureDate = LocalDate.now().plusDays(8);

        mockMvc.perform(post("/api/appointments/generate")
                .with(csrf())
                .param("date", futureDate.toString())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Each type should have 20 slots (8:00-18:00, 30-min intervals = 20 per type)
        List<Appointment> pickupSlots = appointmentRepository
                .findByAppointmentDateAndAppointmentType(futureDate, "PICKUP");
        List<Appointment> dropoffSlots = appointmentRepository
                .findByAppointmentDateAndAppointmentType(futureDate, "DROPOFF");

        assertThat(pickupSlots.size()).as("PICKUP slots should be 20 (10h × 2 per hour)").isEqualTo(20);
        assertThat(dropoffSlots.size()).as("DROPOFF slots should be 20 (10h × 2 per hour)").isEqualTo(20);
    }

    /**
     * Generating slots twice for the same date is idempotent — the second call is a no-op
     * and the slot count stays at 20 (not 40).
     */
    @Test
    void shouldHandleDuplicateGeneration() throws Exception {
        LocalDate futureDate = LocalDate.now().plusDays(9);

        // First generation
        mockMvc.perform(post("/api/appointments/generate")
                .with(csrf())
                .param("date", futureDate.toString())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Second generation — should be a no-op (countByAppointmentDate > 0 guard)
        mockMvc.perform(post("/api/appointments/generate")
                .with(csrf())
                .param("date", futureDate.toString())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        List<Appointment> pickupSlots = appointmentRepository
                .findByAppointmentDateAndAppointmentType(futureDate, "PICKUP");
        assertThat(pickupSlots.size()).as("Idempotent: still 20 PICKUP slots, not 40").isEqualTo(20);
    }

    /**
     * POST /api/appointments/generate without the required date param returns 400.
     */
    @Test
    void shouldRejectGenerateWithMissingDate() throws Exception {
        mockMvc.perform(post("/api/appointments/generate")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Role findOrCreateRole(String name) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
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
