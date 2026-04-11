package com.reclaim.portal.service;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.contracts.entity.ContractInstance;
import com.reclaim.portal.contracts.entity.ContractTemplate;
import com.reclaim.portal.contracts.entity.ContractTemplateVersion;
import com.reclaim.portal.contracts.entity.SignatureArtifact;
import com.reclaim.portal.contracts.service.ContractService;
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

/**
 * Tests that printable contract model includes signature artifact data.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContractSignaturePrintTest {

    @Autowired private ContractService contractService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long userId;
    private Long reviewerId;
    private Order testOrder;
    private ContractTemplateVersion templateVersion;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_ADMIN"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        Role reviewerRole = roleRepository.findByName("ROLE_REVIEWER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_REVIEWER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User user = new User();
        user.setUsername("sig_print_user_" + System.nanoTime());
        user.setPasswordHash(passwordEncoder.encode("TestPass1!abc"));
        user.setEnabled(true); user.setLocked(false); user.setForcePasswordReset(false);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>(Set.of(adminRole)));
        user = userRepository.save(user);
        userId = user.getId();

        User reviewer = new User();
        reviewer.setUsername("sig_print_rev_" + System.nanoTime());
        reviewer.setPasswordHash(passwordEncoder.encode("TestPass1!abc"));
        reviewer.setEnabled(true); reviewer.setLocked(false); reviewer.setForcePasswordReset(false);
        reviewer.setFailedAttempts(0);
        reviewer.setCreatedAt(LocalDateTime.now()); reviewer.setUpdatedAt(LocalDateTime.now());
        reviewer.setRoles(new HashSet<>(Set.of(reviewerRole)));
        reviewer = userRepository.save(reviewer);
        reviewerId = reviewer.getId();

        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(3));
        appt.setStartTime("10:00"); appt.setEndTime("10:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(5); appt.setSlotsBooked(1);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        testOrder = new Order();
        testOrder.setUserId(userId);
        testOrder.setAppointmentId(appt.getId());
        testOrder.setOrderStatus("ACCEPTED");
        testOrder.setAppointmentType("PICKUP");
        testOrder.setRescheduleCount(0);
        testOrder.setCurrency("USD");
        testOrder.setTotalPrice(new BigDecimal("25.00"));
        testOrder.setCreatedAt(LocalDateTime.now()); testOrder.setUpdatedAt(LocalDateTime.now());
        testOrder = orderRepository.save(testOrder);

        ContractTemplate template = contractService.createTemplate("Sig Print Test", "test", userId);
        templateVersion = contractService.createTemplateVersion(template.getId(), "Content", "v1", userId);
    }

    @Test
    void signedContractHasSignatureArtifact() {
        ContractInstance instance = contractService.initiateContract(
                testOrder.getId(), templateVersion.getId(), userId, reviewerId,
                null, LocalDate.now(), LocalDate.now().plusYears(1));
        contractService.confirmContract(instance.getId(), reviewerId);
        contractService.signContract(instance.getId(), userId, "DRAWN", createMinimalPng());

        SignatureArtifact artifact = contractService.getSignatureArtifact(instance.getId());

        assertThat(artifact).isNotNull();
        assertThat(artifact.getContractId()).isEqualTo(instance.getId());
        assertThat(artifact.getSignerId()).isEqualTo(userId);
        assertThat(artifact.getSignatureType()).isEqualTo("DRAWN");
        assertThat(artifact.getFilePath()).isNotNull();
        assertThat(artifact.getChecksum()).isNotNull();
        assertThat(artifact.getSignatureHash()).isNotNull();
    }

    @Test
    void unsignedContractHasNoSignatureArtifact() {
        ContractInstance instance = contractService.initiateContract(
                testOrder.getId(), templateVersion.getId(), userId, reviewerId,
                null, LocalDate.now(), LocalDate.now().plusYears(1));

        SignatureArtifact artifact = contractService.getSignatureArtifact(instance.getId());

        assertThat(artifact).isNull();
    }

    private byte[] createMinimalPng() {
        return new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
            (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xFF, (byte) 0xFF, 0x3F,
            0x00, 0x05, (byte) 0xFE, 0x02, (byte) 0xFE, (byte) 0xDC, (byte) 0xCC, 0x59,
            (byte) 0xE7, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
            0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}
