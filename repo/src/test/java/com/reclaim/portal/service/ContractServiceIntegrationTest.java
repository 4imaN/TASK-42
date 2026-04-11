package com.reclaim.portal.service;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.contracts.entity.ContractInstance;
import com.reclaim.portal.contracts.entity.ContractTemplate;
import com.reclaim.portal.contracts.entity.ContractTemplateVersion;
import com.reclaim.portal.contracts.repository.ContractTemplateRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateVersionRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContractServiceIntegrationTest {

    @Autowired
    private ContractService contractService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ContractTemplateRepository templateRepository;

    @Autowired
    private ContractTemplateVersionRepository versionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Order testOrder;
    private ContractTemplateVersion templateVersion;
    private Long adminUserId;
    private Long reviewerUserId;

    @BeforeEach
    void setUp() {
        // Create users needed for FK constraints
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_ADMIN");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User adminUser = new User();
        adminUser.setUsername("contract_admin_" + System.nanoTime());
        adminUser.setPasswordHash(passwordEncoder.encode("AdminPass1!"));
        adminUser.setEnabled(true);
        adminUser.setLocked(false);
        adminUser.setForcePasswordReset(false);
        adminUser.setFailedAttempts(0);
        adminUser.setCreatedAt(LocalDateTime.now());
        adminUser.setUpdatedAt(LocalDateTime.now());
        adminUser.setRoles(new HashSet<>(Set.of(adminRole)));
        adminUser = userRepository.save(adminUser);
        adminUserId = adminUser.getId();

        Role reviewerRole = roleRepository.findByName("ROLE_REVIEWER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_REVIEWER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User reviewerUser = new User();
        reviewerUser.setUsername("contract_reviewer_" + System.nanoTime());
        reviewerUser.setPasswordHash(passwordEncoder.encode("ReviewerPass1!"));
        reviewerUser.setEnabled(true);
        reviewerUser.setLocked(false);
        reviewerUser.setForcePasswordReset(false);
        reviewerUser.setFailedAttempts(0);
        reviewerUser.setCreatedAt(LocalDateTime.now());
        reviewerUser.setUpdatedAt(LocalDateTime.now());
        reviewerUser.setRoles(new HashSet<>(Set.of(reviewerRole)));
        reviewerUser = userRepository.save(reviewerUser);
        reviewerUserId = reviewerUser.getId();

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
        testOrder.setUserId(adminUserId);
        testOrder.setAppointmentId(appointment.getId());
        testOrder.setOrderStatus("ACCEPTED");
        testOrder.setAppointmentType("PICKUP");
        testOrder.setRescheduleCount(0);
        testOrder.setCurrency("USD");
        testOrder.setTotalPrice(new BigDecimal("15.00"));
        testOrder.setCreatedAt(LocalDateTime.now());
        testOrder.setUpdatedAt(LocalDateTime.now());
        testOrder = orderRepository.save(testOrder);

        ContractTemplate template = contractService.createTemplate(
            "Standard Recycling Contract", "Standard contract template", adminUserId);

        templateVersion = contractService.createTemplateVersion(
            template.getId(), "This is a contract for {{partyName}}.", "Initial version", adminUserId);

        // Register the partyName clause field so rendering can substitute {{partyName}}
        contractService.addClauseField(
            templateVersion.getId(), "partyName", "TEXT", "Party Name", true, "Default Party", 1);
    }

    @Test
    void shouldInitiateContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            "partyName=John Doe",
            LocalDate.now(), LocalDate.now().plusYears(1)
        );

        assertThat(instance).isNotNull();
        assertThat(instance.getId()).isNotNull();
        assertThat(instance.getContractStatus()).isEqualTo("INITIATED");
        assertThat(instance.getRenderedContent()).contains("John Doe");
    }

    @Test
    void shouldConfirmContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            "partyName=Jane Doe",
            LocalDate.now(), LocalDate.now().plusYears(1)
        );

        ContractInstance confirmed = contractService.confirmContract(instance.getId(), reviewerUserId);

        assertThat(confirmed.getContractStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void shouldSignContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            "partyName=Signer",
            LocalDate.now(), LocalDate.now().plusYears(1)
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);

        // Provide minimal PNG bytes (1x1 pixel PNG)
        byte[] minimalPng = createMinimalPng();
        ContractInstance signed = contractService.signContract(
            instance.getId(), adminUserId, "DIGITAL", minimalPng);

        assertThat(signed.getContractStatus()).isEqualTo("SIGNED");
        assertThat(signed.getSignedAt()).isNotNull();
    }

    @Test
    void shouldArchiveContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null,
            LocalDate.now(), LocalDate.now().plusYears(1)
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);
        byte[] minimalPng = createMinimalPng();
        contractService.signContract(instance.getId(), adminUserId, "DIGITAL", minimalPng);

        ContractInstance archived = contractService.archiveContract(instance.getId());

        assertThat(archived.getContractStatus()).isEqualTo("ARCHIVED");
        assertThat(archived.getArchivedAt()).isNotNull();
    }

    @Test
    void shouldDeriveExpiringSoonStatus() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null,
            LocalDate.now(), LocalDate.now().plusDays(15) // within 30-day expiring soon window
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);
        byte[] minimalPng = createMinimalPng();
        ContractInstance signed = contractService.signContract(instance.getId(), adminUserId, "DIGITAL", minimalPng);

        String status = contractService.getContractStatus(signed);

        assertThat(status).isEqualTo("EXPIRING_SOON");
    }

    @Test
    void shouldDeriveActiveStatus() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null,
            LocalDate.now(), LocalDate.now().plusYears(1)
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);
        byte[] minimalPng = createMinimalPng();
        ContractInstance signed = contractService.signContract(instance.getId(), adminUserId, "DIGITAL", minimalPng);

        String status = contractService.getContractStatus(signed);

        assertThat(status).isEqualTo("ACTIVE");
    }

    @Test
    void shouldRejectConfirmingNonInitiatedContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null,
            LocalDate.now(), LocalDate.now().plusYears(1)
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);

        // Cannot confirm again (already CONFIRMED)
        assertThatThrownBy(() -> contractService.confirmContract(instance.getId(), reviewerUserId))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("INITIATED");
    }

    @Test
    void shouldTerminateSignedContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(1)
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);
        contractService.signContract(instance.getId(), adminUserId, "DIGITAL", createMinimalPng());

        ContractInstance terminated = contractService.terminateContract(instance.getId());

        assertThat(terminated.getContractStatus()).isEqualTo("TERMINATED");
    }

    @Test
    void shouldRejectTerminatingContractInWrongState() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(1)
        );
        // INITIATED state - cannot terminate

        assertThatThrownBy(() -> contractService.terminateContract(instance.getId()))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("ACTIVE, SIGNED, or RENEWED");
    }

    @Test
    void shouldVoidContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(1)
        );
        // INITIATED state - can be voided

        ContractInstance voided = contractService.voidContract(instance.getId());

        assertThat(voided.getContractStatus()).isEqualTo("VOIDED");
    }

    @Test
    void shouldRejectVoidingArchivedContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(1)
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);
        contractService.signContract(instance.getId(), adminUserId, "DIGITAL", createMinimalPng());
        contractService.archiveContract(instance.getId());

        assertThatThrownBy(() -> contractService.voidContract(instance.getId()))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("ARCHIVED");
    }

    @Test
    void shouldRenewSignedContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(1)
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);
        contractService.signContract(instance.getId(), adminUserId, "DIGITAL", createMinimalPng());

        LocalDate newEndDate = LocalDate.now().plusYears(2);
        ContractInstance renewed = contractService.renewContract(instance.getId(), adminUserId, newEndDate);

        assertThat(renewed.getContractStatus()).isEqualTo("RENEWED");
        assertThat(renewed.getEndDate()).isEqualTo(newEndDate);
    }

    @Test
    void shouldRejectRenewingContractInWrongState() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(1)
        );
        // INITIATED state - cannot renew

        assertThatThrownBy(() -> contractService.renewContract(instance.getId(), adminUserId,
            LocalDate.now().plusYears(2)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("ACTIVE or SIGNED");
    }

    @Test
    void shouldGetUserContracts() {
        contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(1)
        );
        contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(2)
        );

        var contracts = contractService.getUserContracts(adminUserId);

        assertThat(contracts).isNotEmpty();
        assertThat(contracts).allMatch(c -> adminUserId.equals(c.getUserId()));
        assertThat(contracts.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldGetContractDetail() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            "partyName=Detail Test",
            LocalDate.now(), LocalDate.now().plusYears(1)
        );

        ContractInstance detail = contractService.getContractDetail(instance.getId());

        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isEqualTo(instance.getId());
        assertThat(detail.getRenderedContent()).contains("Detail Test");
    }

    @Test
    void shouldGetPrintableContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            "partyName=Print Test",
            LocalDate.now(), LocalDate.now().plusYears(1)
        );

        ContractInstance printable = contractService.getPrintableContract(instance.getId());

        assertThat(printable).isNotNull();
        assertThat(printable.getId()).isEqualTo(instance.getId());
        assertThat(printable.getRenderedContent()).contains("Print Test");
    }

    @Test
    void shouldThrowForGetContractDetailWithUnknownId() {
        assertThatThrownBy(() -> contractService.getContractDetail(999999L))
            .isInstanceOf(com.reclaim.portal.common.exception.EntityNotFoundException.class);
    }

    @Test
    void shouldThrowForGetPrintableContractWithUnknownId() {
        assertThatThrownBy(() -> contractService.getPrintableContract(999999L))
            .isInstanceOf(com.reclaim.portal.common.exception.EntityNotFoundException.class);
    }

    @Test
    void shouldTerminateRenewedContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(1)
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);
        contractService.signContract(instance.getId(), adminUserId, "DIGITAL", createMinimalPng());
        contractService.renewContract(instance.getId(), adminUserId, LocalDate.now().plusYears(2));

        ContractInstance terminated = contractService.terminateContract(instance.getId());

        assertThat(terminated.getContractStatus()).isEqualTo("TERMINATED");
    }

    @Test
    void shouldDeriveTerminatedStatusFromExpiredEndDate() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null,
            LocalDate.now().minusYears(2), LocalDate.now().minusDays(1) // end date in the past
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);
        byte[] minimalPng = createMinimalPng();
        ContractInstance signed = contractService.signContract(instance.getId(), adminUserId, "DIGITAL", minimalPng);

        String status = contractService.getContractStatus(signed);
        assertThat(status).isEqualTo("TERMINATED");
    }

    @Test
    void shouldReturnRenewedStatusDirectly() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(5)
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);
        contractService.signContract(instance.getId(), adminUserId, "DIGITAL", createMinimalPng());
        ContractInstance renewed = contractService.renewContract(instance.getId(), adminUserId,
            LocalDate.now().plusYears(5));

        String status = contractService.getContractStatus(renewed);
        assertThat(status).isEqualTo("RENEWED");
    }

    @Test
    void shouldReturnTerminatedStatusForTerminatedContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(1)
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);
        contractService.signContract(instance.getId(), adminUserId, "DIGITAL", createMinimalPng());
        ContractInstance terminated = contractService.terminateContract(instance.getId());

        String status = contractService.getContractStatus(terminated);
        assertThat(status).isEqualTo("TERMINATED");
    }

    @Test
    void shouldReturnVoidedStatusForVoidedContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(1)
        );
        ContractInstance voided = contractService.voidContract(instance.getId());

        String status = contractService.getContractStatus(voided);
        assertThat(status).isEqualTo("VOIDED");
    }

    @Test
    void shouldReturnArchivedStatusForArchivedContract() {
        ContractInstance instance = contractService.initiateContract(
            testOrder.getId(), templateVersion.getId(), adminUserId, reviewerUserId,
            null, LocalDate.now(), LocalDate.now().plusYears(1)
        );
        contractService.confirmContract(instance.getId(), reviewerUserId);
        contractService.signContract(instance.getId(), adminUserId, "DIGITAL", createMinimalPng());
        ContractInstance archived = contractService.archiveContract(instance.getId());

        String status = contractService.getContractStatus(archived);
        assertThat(status).isEqualTo("ARCHIVED");
    }

    // Minimal 1x1 white PNG for testing
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
