package com.reclaim.portal.service;

import com.reclaim.portal.appeals.entity.Appeal;
import com.reclaim.portal.appeals.service.AppealService;
import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AppealServiceIntegrationTest {

    @Autowired
    private AppealService appealService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Order testOrder;
    private Long appellantId;
    private Long adjudicatorId;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_ADMIN");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User appellant = new User();
        appellant.setUsername("appeal_appellant_" + System.nanoTime());
        appellant.setPasswordHash(passwordEncoder.encode("Pass1!wordXXX"));
        appellant.setEnabled(true);
        appellant.setLocked(false);
        appellant.setForcePasswordReset(false);
        appellant.setFailedAttempts(0);
        appellant.setCreatedAt(LocalDateTime.now());
        appellant.setUpdatedAt(LocalDateTime.now());
        appellant.setRoles(new HashSet<>(Set.of(userRole)));
        appellant = userRepository.save(appellant);
        appellantId = appellant.getId();

        User adjudicator = new User();
        adjudicator.setUsername("appeal_admin_" + System.nanoTime());
        adjudicator.setPasswordHash(passwordEncoder.encode("Pass1!wordXXX"));
        adjudicator.setEnabled(true);
        adjudicator.setLocked(false);
        adjudicator.setForcePasswordReset(false);
        adjudicator.setFailedAttempts(0);
        adjudicator.setCreatedAt(LocalDateTime.now());
        adjudicator.setUpdatedAt(LocalDateTime.now());
        adjudicator.setRoles(new HashSet<>(Set.of(adminRole)));
        adjudicator = userRepository.save(adjudicator);
        adjudicatorId = adjudicator.getId();

        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(LocalDate.now().plusDays(3));
        appointment.setStartTime("10:00");
        appointment.setEndTime("10:30");
        appointment.setAppointmentType("PICKUP");
        appointment.setSlotsAvailable(5);
        appointment.setSlotsBooked(1);
        appointment.setCreatedAt(LocalDateTime.now());
        appointment = appointmentRepository.save(appointment);

        testOrder = new Order();
        testOrder.setUserId(appellantId);
        testOrder.setAppointmentId(appointment.getId());
        testOrder.setOrderStatus("COMPLETED");
        testOrder.setAppointmentType("PICKUP");
        testOrder.setRescheduleCount(0);
        testOrder.setCurrency("USD");
        testOrder.setTotalPrice(new BigDecimal("20.00"));
        testOrder.setCreatedAt(LocalDateTime.now());
        testOrder.setUpdatedAt(LocalDateTime.now());
        testOrder = orderRepository.save(testOrder);
    }

    @Test
    void shouldCreateAppeal() {
        Appeal appeal = appealService.createAppeal(testOrder.getId(), null, appellantId, "Damaged items received");

        assertThat(appeal).isNotNull();
        assertThat(appeal.getId()).isNotNull();
        assertThat(appeal.getAppealStatus()).isEqualTo("OPEN");
        assertThat(appeal.getReason()).isEqualTo("Damaged items received");
        assertThat(appeal.getAppellantId()).isEqualTo(appellantId);
    }

    @Test
    void shouldResolveAppeal() {
        Appeal appeal = appealService.createAppeal(testOrder.getId(), null, appellantId, "Wrong items");

        Appeal resolved = appealService.resolveAppeal(appeal.getId(), adjudicatorId, "UPHELD", "Items confirmed wrong");

        assertThat(resolved.getAppealStatus()).isEqualTo("RESOLVED");
    }

    @Test
    void shouldRejectResolvingClosed() {
        Appeal appeal = appealService.createAppeal(testOrder.getId(), null, appellantId, "Initial reason");
        appealService.resolveAppeal(appeal.getId(), adjudicatorId, "DENIED", "No evidence");

        // Attempt to resolve again (already RESOLVED)
        assertThatThrownBy(() -> appealService.resolveAppeal(appeal.getId(), adjudicatorId, "UPHELD", "Second attempt"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("OPEN");
    }

    @Test
    void shouldGetAppealDetails() {
        Appeal appeal = appealService.createAppeal(testOrder.getId(), null, appellantId, "Billing issue");

        var details = appealService.getAppealDetails(appeal.getId());

        assertThat(details).containsKey("appeal");
        assertThat(details).containsKey("evidence");
        assertThat(details).containsKey("outcome");
    }

    @Test
    void shouldGetAppealsForUser() {
        appealService.createAppeal(testOrder.getId(), null, appellantId, "First appeal");

        var appeals = appealService.getAppealsForUser(appellantId);

        assertThat(appeals).isNotEmpty();
        assertThat(appeals).allMatch(a -> a.getAppellantId().equals(appellantId));
    }
}
