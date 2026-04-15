package com.reclaim.portal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.appeals.entity.EvidenceFile;
import com.reclaim.portal.appeals.repository.EvidenceFileRepository;
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
import com.reclaim.portal.contracts.entity.SignatureArtifact;
import com.reclaim.portal.contracts.repository.ContractClauseFieldRepository;
import com.reclaim.portal.contracts.repository.ContractInstanceRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateVersionRepository;
import com.reclaim.portal.contracts.repository.SignatureArtifactRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Task 2: Deep contract file/signature/evidence endpoint tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContractFileEndpointsTest {

    // Minimal valid JPEG bytes (SOI + APP0 header)
    private static final byte[] MIN_JPEG = {
        (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0x00,0x10,0x4A,0x46,
        0x49,0x46,0x00,0x01,0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00
    };

    // Minimal valid 1x1 PNG bytes
    private static final byte[] MIN_PNG = {
        (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,
        0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,
        0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,
        0x08,0x02,0x00,0x00,0x00,(byte)0x90,0x77,0x53,
        (byte)0xDE,0x00,0x00,0x00,0x0C,0x49,0x44,0x41,
        0x54,0x08,(byte)0xD7,0x63,(byte)0xF8,(byte)0xFF,(byte)0xFF,0x3F,
        0x00,0x05,(byte)0xFE,0x02,(byte)0xFE,(byte)0xDC,(byte)0xCC,0x59,
        (byte)0xE7,0x00,0x00,0x00,0x00,0x49,0x45,0x4E,
        0x44,(byte)0xAE,0x42,0x60,(byte)0x82
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ContractTemplateRepository templateRepository;
    @Autowired private ContractTemplateVersionRepository versionRepository;
    @Autowired private ContractClauseFieldRepository clauseFieldRepository;
    @Autowired private ContractInstanceRepository instanceRepository;
    @Autowired private SignatureArtifactRepository signatureArtifactRepository;
    @Autowired private EvidenceFileRepository evidenceFileRepository;

    private User ownerUser;
    private User reviewerUser;
    private User adminUser;
    private User strangerUser;

    private String ownerToken;
    private String reviewerToken;
    private String adminToken;
    private String strangerToken;

    private Long contractId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role adminRole    = findOrCreateRole("ROLE_ADMIN");

        ownerUser    = createUser("cfe_owner_"    + nonce, userRole);
        reviewerUser = createUser("cfe_reviewer_" + nonce, reviewerRole);
        adminUser    = createUser("cfe_admin_"    + nonce, adminRole);
        strangerUser = createUser("cfe_stranger_" + nonce, userRole);

        ownerToken    = jwtService.generateAccessToken(ownerUser);
        reviewerToken = jwtService.generateAccessToken(reviewerUser);
        adminToken    = jwtService.generateAccessToken(adminUser);
        strangerToken = jwtService.generateAccessToken(strangerUser);

        // Create appointment
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(5));
        appt.setStartTime("09:00");
        appt.setEndTime("09:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(10);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        // ACCEPTED order owned by ownerUser, assigned to reviewerUser
        Order order = new Order();
        order.setUserId(ownerUser.getId());
        order.setAppointmentId(appt.getId());
        order.setOrderStatus("ACCEPTED");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(BigDecimal.TEN);
        order.setReviewerId(reviewerUser.getId());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        // Contract template + version + clause field with default value
        ContractTemplate template = new ContractTemplate();
        template.setName("CFE Template " + nonce);
        template.setDescription("Contract file endpoint test template");
        template.setActive(true);
        template.setCreatedBy(adminUser.getId());
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        template = templateRepository.save(template);

        ContractTemplateVersion version = new ContractTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNumber(1);
        version.setContent("Contract for {{partyName}}");
        version.setChangeNotes("Initial");
        version.setCreatedBy(adminUser.getId());
        version.setCreatedAt(LocalDateTime.now());
        version = versionRepository.save(version);

        // Required partyName with default so contracts can be initiated without providing fieldValues
        ContractClauseField field = new ContractClauseField();
        field.setTemplateVersionId(version.getId());
        field.setFieldName("partyName");
        field.setFieldType("TEXT");
        field.setFieldLabel("Party Name");
        field.setRequired(true);
        field.setDefaultValue("Default Party");
        field.setDisplayOrder(1);
        clauseFieldRepository.save(field);

        // Create INITIATED contract instance
        ContractInstance instance = new ContractInstance();
        instance.setOrderId(order.getId());
        instance.setTemplateVersionId(version.getId());
        instance.setUserId(ownerUser.getId());
        instance.setReviewerId(reviewerUser.getId());
        instance.setContractStatus("INITIATED");
        instance.setRenderedContent("Contract for Default Party");
        instance.setFieldValues("{}");
        instance.setStartDate(LocalDate.of(2099, 1, 1));
        instance.setEndDate(LocalDate.of(2099, 12, 31));
        instance.setCreatedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);
        contractId = instance.getId();
    }

    // =========================================================================
    // Helper: advance contract to CONFIRMED state
    // =========================================================================

    private void advanceToConfirmed() throws Exception {
        mockMvc.perform(put("/api/contracts/" + contractId + "/confirm")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractStatus").value("CONFIRMED"));
    }

    // =========================================================================
    // Test 1: Sign contract as owner with drawn signature
    // =========================================================================

    @Test
    void shouldSignContractAsOwnerWithDrawnSignature() throws Exception {
        advanceToConfirmed();

        MockMultipartFile sigFile = new MockMultipartFile(
                "file", "signature.png", "image/png", MIN_PNG);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/contracts/" + contractId + "/sign")
                .file(sigFile)
                .param("signatureType", "DRAWN")
                .with(csrf())
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractStatus").value("SIGNED"))
                .andExpect(jsonPath("$.signedAt").exists());

        // Verify signature artifact was persisted
        List<SignatureArtifact> artifacts = signatureArtifactRepository.findByContractId(contractId);
        assertThat(artifacts).isNotEmpty();
        assertThat(artifacts.get(0).getFilePath()).isNotBlank();
        assertThat(artifacts.get(0).getChecksum()).isNotBlank();
    }

    // =========================================================================
    // Test 2: Reject signing as non-owner (reviewer is not the contract user)
    // =========================================================================

    @Test
    void shouldRejectSigningAsNonOwner() throws Exception {
        advanceToConfirmed();

        MockMultipartFile sigFile = new MockMultipartFile(
                "file", "signature.png", "image/png", MIN_PNG);

        // Reviewer is NOT the contract's userId — service throws "Access denied to contract"
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/contracts/" + contractId + "/sign")
                .file(sigFile)
                .param("signatureType", "DRAWN")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Test 3: Reject signing when contract not in CONFIRMED state
    // =========================================================================

    @Test
    void shouldRejectSigningWithoutConfirmedState() throws Exception {
        // Contract is still INITIATED — signing requires CONFIRMED
        MockMultipartFile sigFile = new MockMultipartFile(
                "file", "signature.png", "image/png", MIN_PNG);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/contracts/" + contractId + "/sign")
                .file(sigFile)
                .param("signatureType", "DRAWN")
                .with(csrf())
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // Test 4: Upload contract evidence as owner
    // =========================================================================

    @Test
    void shouldUploadContractEvidenceAsOwner() throws Exception {
        MockMultipartFile evidenceFile = new MockMultipartFile(
                "file", "evidence.jpg", "image/jpeg", MIN_JPEG);

        mockMvc.perform(multipart("/api/contracts/" + contractId + "/evidence")
                .file(evidenceFile)
                .with(csrf())
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").exists())
                .andExpect(jsonPath("$.filePath").exists())
                .andExpect(jsonPath("$.checksum").exists());

        // Verify evidence file was persisted with correct entityType/entityId
        List<EvidenceFile> evidenceFiles = evidenceFileRepository
                .findByEntityTypeAndEntityId("CONTRACT", contractId);
        assertThat(evidenceFiles).isNotEmpty();
    }

    // =========================================================================
    // Test 5: Reject evidence upload as stranger
    // =========================================================================

    @Test
    void shouldRejectEvidenceUploadAsStranger() throws Exception {
        MockMultipartFile evidenceFile = new MockMultipartFile(
                "file", "evidence.jpg", "image/jpeg", MIN_JPEG);

        // Stranger has no access to this contract
        mockMvc.perform(multipart("/api/contracts/" + contractId + "/evidence")
                .file(evidenceFile)
                .with(csrf())
                .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Test 6: List contract evidence as owner
    // =========================================================================

    @Test
    void shouldListContractEvidence() throws Exception {
        // First upload evidence
        MockMultipartFile evidenceFile = new MockMultipartFile(
                "file", "proof.jpg", "image/jpeg", MIN_JPEG);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/contracts/" + contractId + "/evidence")
                .file(evidenceFile)
                .with(csrf())
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        String uploadedFileName = objectMapper.readTree(uploadResult.getResponse().getContentAsString())
                .get("fileName").asText();

        // Then list evidence
        MvcResult listResult = mockMvc.perform(get("/api/contracts/" + contractId + "/evidence")
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isGreaterThanOrEqualTo(1);

        boolean found = false;
        for (var item : body) {
            if (uploadedFileName.equals(item.get("fileName").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).as("uploaded evidence fileName '%s' should appear in list", uploadedFileName).isTrue();
    }

    // =========================================================================
    // Test 7: Deny evidence listing to non-participant
    // =========================================================================

    @Test
    void shouldDenyEvidenceListingToNonParticipant() throws Exception {
        mockMvc.perform(get("/api/contracts/" + contractId + "/evidence")
                .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Test 8: Serve signed signature file to owner via storage
    // =========================================================================

    @Test
    void shouldServeSignedSignatureFileToOwnerViaStorage() throws Exception {
        advanceToConfirmed();

        MockMultipartFile sigFile = new MockMultipartFile(
                "file", "signature.png", "image/png", MIN_PNG);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/contracts/" + contractId + "/sign")
                .file(sigFile)
                .param("signatureType", "DRAWN")
                .with(csrf())
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Get the stored file path
        List<SignatureArtifact> artifacts = signatureArtifactRepository.findByContractId(contractId);
        assertThat(artifacts).isNotEmpty();
        String filePath = artifacts.get(0).getFilePath(); // e.g. "signatures/<uuid>.png"

        MvcResult storageResult = mockMvc.perform(get("/storage/" + filePath)
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        // Content type should be image/png or application/octet-stream
        String contentType = storageResult.getResponse().getContentType();
        assertThat(contentType).isNotNull();
        assertThat(contentType).containsAnyOf("image/png", "application/octet-stream");

        // Byte length should match what was uploaded
        byte[] responseBytes = storageResult.getResponse().getContentAsByteArray();
        assertThat(responseBytes.length).isEqualTo(MIN_PNG.length);
    }

    // =========================================================================
    // Test 9: Deny storage signature access to other user
    // =========================================================================

    @Test
    void shouldDenyStorageSignatureAccessToOtherUser() throws Exception {
        advanceToConfirmed();

        MockMultipartFile sigFile = new MockMultipartFile(
                "file", "signature.png", "image/png", MIN_PNG);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/contracts/" + contractId + "/sign")
                .file(sigFile)
                .param("signatureType", "DRAWN")
                .with(csrf())
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        List<SignatureArtifact> artifacts = signatureArtifactRepository.findByContractId(contractId);
        assertThat(artifacts).isNotEmpty();
        String filePath = artifacts.get(0).getFilePath();

        // Stranger should not have access to this signature
        mockMvc.perform(get("/storage/" + filePath)
                .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Test 10: Allow admin storage access
    // =========================================================================

    @Test
    void shouldAllowAdminStorageAccess() throws Exception {
        advanceToConfirmed();

        MockMultipartFile sigFile = new MockMultipartFile(
                "file", "signature.png", "image/png", MIN_PNG);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/contracts/" + contractId + "/sign")
                .file(sigFile)
                .param("signatureType", "DRAWN")
                .with(csrf())
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        List<SignatureArtifact> artifacts = signatureArtifactRepository.findByContractId(contractId);
        assertThat(artifacts).isNotEmpty();
        String filePath = artifacts.get(0).getFilePath();

        // Admin can access all files
        mockMvc.perform(get("/storage/" + filePath)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Test 11: Reject invalid signature file type (bad magic bytes)
    // =========================================================================

    @Test
    void shouldRejectInvalidSignatureFileType() throws Exception {
        advanceToConfirmed();

        // Random bytes that don't match PNG magic bytes
        byte[] invalidBytes = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13};

        MockMultipartFile badFile = new MockMultipartFile(
                "file", "fake.png", "image/png", invalidBytes);

        // StorageService validates magic bytes — should reject with 409
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/contracts/" + contractId + "/sign")
                .file(badFile)
                .param("signatureType", "DRAWN")
                .with(csrf())
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
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
