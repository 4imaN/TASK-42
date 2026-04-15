package com.reclaim.portal.e2e;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-HTTP coverage for appointment availability and slot generation endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpAppointmentsTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private JwtService jwtService;

    private String userToken;
    private String adminToken;
    private LocalDate slotDate;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole  = findOrCreateRole("ROLE_USER");
        Role adminRole = findOrCreateRole("ROLE_ADMIN");

        User user  = createUser("appt_user_"  + nonce, userRole);
        User admin = createUser("appt_admin_" + nonce, adminRole);

        userToken  = jwtService.generateAccessToken(user);
        adminToken = jwtService.generateAccessToken(admin);

        // Seed a PICKUP slot 3 days from today
        slotDate = LocalDate.now().plusDays(3);

        Appointment appt = new Appointment();
        appt.setAppointmentDate(slotDate);
        appt.setStartTime("10:00");
        appt.setEndTime("10:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(10);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appointmentRepository.save(appt);
    }

    // =========================================================================
    // 1. Get available slots
    // =========================================================================

    @Test
    void shouldGetAvailableSlotsOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                base() + "/api/appointments/available?date=" + slotDate + "&type=PICKUP",
                HttpMethod.GET, req,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
    }

    // =========================================================================
    // 2. Filter slots by type
    // =========================================================================

    @Test
    void shouldFilterSlotsByTypeOverRealHttp() {
        long nonce = System.nanoTime();
        LocalDate filterDate = LocalDate.now().plusDays(4);

        // Seed a PICKUP and a DROPOFF for the same date
        Appointment pickup = new Appointment();
        pickup.setAppointmentDate(filterDate);
        pickup.setStartTime("09:00");
        pickup.setEndTime("09:30");
        pickup.setAppointmentType("PICKUP");
        pickup.setSlotsAvailable(5);
        pickup.setSlotsBooked(0);
        pickup.setCreatedAt(LocalDateTime.now());
        appointmentRepository.save(pickup);

        Appointment dropoff = new Appointment();
        dropoff.setAppointmentDate(filterDate);
        dropoff.setStartTime("09:30");
        dropoff.setEndTime("10:00");
        dropoff.setAppointmentType("DROPOFF");
        dropoff.setSlotsAvailable(5);
        dropoff.setSlotsBooked(0);
        dropoff.setCreatedAt(LocalDateTime.now());
        appointmentRepository.save(dropoff);

        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                base() + "/api/appointments/available?date=" + filterDate + "&type=PICKUP",
                HttpMethod.GET, req,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull().isNotEmpty();

        boolean allPickup = resp.getBody().stream()
                .allMatch(slot -> "PICKUP".equals(slot.get("appointmentType")));
        assertThat(allPickup).as("all slots returned should have appointmentType=PICKUP").isTrue();
    }

    // =========================================================================
    // 3. Missing type param → 400
    // =========================================================================

    @Test
    void shouldRejectAvailableWithMissingTypeOverRealHttp() {
        HttpEntity<Void> req = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/appointments/available?date=" + slotDate,
                HttpMethod.GET, req, String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // =========================================================================
    // 4. Admin can generate slots
    // =========================================================================

    @Test
    void shouldGenerateSlotsAsAdminOverRealHttp() {
        LocalDate genDate = LocalDate.now().plusDays(7);

        HttpEntity<Void> genReq = new HttpEntity<>(csrfAuthHeaders(adminToken));
        ResponseEntity<Void> genResp = restTemplate.exchange(
                base() + "/api/appointments/generate?date=" + genDate,
                HttpMethod.POST, genReq, Void.class);

        assertThat(genResp.getStatusCode().value()).isEqualTo(200);

        // Slots should now be available
        HttpEntity<Void> getReq = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<List<Map<String, Object>>> getResp = restTemplate.exchange(
                base() + "/api/appointments/available?date=" + genDate + "&type=PICKUP",
                HttpMethod.GET, getReq,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(getResp.getStatusCode().value()).isEqualTo(200);
        assertThat(getResp.getBody()).isNotNull().isNotEmpty();
    }

    // =========================================================================
    // 5. Regular user cannot generate slots → 403
    // =========================================================================

    @Test
    void shouldRejectGenerateSlotsAsUserOverRealHttp() {
        LocalDate genDate = LocalDate.now().plusDays(8);

        HttpEntity<Void> req = new HttpEntity<>(csrfAuthHeaders(userToken));
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/appointments/generate?date=" + genDate,
                HttpMethod.POST, req, String.class);

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
