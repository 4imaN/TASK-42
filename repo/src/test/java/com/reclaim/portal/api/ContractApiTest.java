package com.reclaim.portal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.contracts.entity.ContractInstance;
import com.reclaim.portal.contracts.entity.ContractTemplate;
import com.reclaim.portal.contracts.entity.ContractTemplateVersion;
import com.reclaim.portal.contracts.repository.ContractInstanceRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateVersionRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContractApiTest {

    // Minimal valid JPEG bytes (SOI + APP0 header)
    private static final byte[] MIN_JPEG = {
        (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0x00,0x10,0x4A,0x46,
        0x49,0x46,0x00,0x01,0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00
    };

    // Minimal valid PNG bytes (1x1 pixel)
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
    @Autowired private ContractInstanceRepository instanceRepository;

    private User adminUser;
    private User reviewerUser;
    private User regularUser;

    private String adminToken;
    private String reviewerToken;
    private String userToken;

    private Long acceptedOrderId;
    private Long templateId;
    private Long versionId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role adminRole  = findOrCreateRole("ROLE_ADMIN");
        Role reviewRole = findOrCreateRole("ROLE_REVIEWER");
        Role userRole   = findOrCreateRole("ROLE_USER");

        adminUser   = createUser("contract_admin_"    + nonce, adminRole);
        reviewerUser = createUser("contract_reviewer_" + nonce, reviewRole);
        regularUser  = createUser("contract_user_"    + nonce, userRole);

        adminToken    = jwtService.generateAccessToken(adminUser);
        reviewerToken = jwtService.generateAccessToken(reviewerUser);
        userToken     = jwtService.generateAccessToken(regularUser);

        // Appointment for the ACCEPTED order
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(5));
        appt.setStartTime("09:00");
        appt.setEndTime("09:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(10);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        // ACCEPTED order owned by regularUser, reviewerId = reviewerUser
        Order order = new Order();
        order.setUserId(regularUser.getId());
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
        acceptedOrderId = order.getId();

        // Contract template + version created directly
        ContractTemplate template = new ContractTemplate();
        template.setName("Standard Template " + nonce);
        template.setDescription("A standard recycling contract");
        template.setActive(true);
        template.setCreatedBy(adminUser.getId());
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        template = templateRepository.save(template);
        templateId = template.getId();

        ContractTemplateVersion version = new ContractTemplateVersion();
        version.setTemplateId(templateId);
        version.setVersionNumber(1);
        version.setContent("Contract for order {{orderId}} between parties.");
        version.setChangeNotes("Initial version");
        version.setCreatedBy(adminUser.getId());
        version.setCreatedAt(LocalDateTime.now());
        version = versionRepository.save(version);
        versionId = version.getId();
    }

    // =========================================================================
    // Template management
    // =========================================================================

    @Test
    void shouldCreateTemplateAsAdmin() throws Exception {
        Map<String, String> body = Map.of("name", "T", "description", "desc");

        MvcResult result = mockMvc.perform(post("/api/contracts/templates")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("T"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        long returnedId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
        assertThat(templateRepository.existsById(returnedId)).isTrue();
    }

    @Test
    void shouldRejectTemplateCreationForUser() throws Exception {
        Map<String, String> body = Map.of("name", "T", "description", "d");

        mockMvc.perform(post("/api/contracts/templates")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateTemplateVersionAsAdmin() throws Exception {
        Map<String, String> body = Map.of("content", "Version content", "changeNotes", "notes");

        MvcResult result = mockMvc.perform(post("/api/contracts/templates/" + templateId + "/versions")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.versionNumber").isNumber())
                .andReturn();

        long returnedVersionId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
        // Verify version is in the latest-first list
        var versions = versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId);
        assertThat(versions.stream().anyMatch(v -> v.getId().equals(returnedVersionId))).isTrue();
    }

    @Test
    void shouldAddClauseFieldAsAdmin() throws Exception {
        Map<String, Object> body = Map.of(
                "fieldName", "partyName",
                "fieldType", "TEXT",
                "fieldLabel", "Party Name",
                "required", true,
                "defaultValue", "",
                "displayOrder", 1
        );

        mockMvc.perform(post("/api/contracts/templates/versions/" + versionId + "/fields")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fieldName").value("partyName"))
                .andExpect(jsonPath("$.required").value(true));
    }

    @Test
    void shouldListActiveTemplates() throws Exception {
        mockMvc.perform(get("/api/contracts/templates")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void shouldGetTemplateVersions() throws Exception {
        mockMvc.perform(get("/api/contracts/templates/" + templateId + "/versions")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldGetClauseFields() throws Exception {
        mockMvc.perform(get("/api/contracts/templates/versions/" + versionId + "/fields")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================================================================
    // Contract lifecycle
    // =========================================================================

    @Test
    void shouldInitiateContractAsReviewer() throws Exception {
        Map<String, Object> body = Map.of(
                "orderId", acceptedOrderId,
                "templateVersionId", versionId,
                "fieldValues", "{}",
                "startDate", "2099-01-01",
                "endDate", "2099-12-31"
        );

        MvcResult result = mockMvc.perform(post("/api/contracts")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractStatus").value("INITIATED"))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        long contractId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
        ContractInstance persisted = instanceRepository.findById(contractId).orElseThrow();
        // contract userId must equal the order owner (regularUser), not the reviewer
        assertThat(persisted.getUserId()).isEqualTo(regularUser.getId());
        assertThat(persisted.getReviewerId()).isEqualTo(reviewerUser.getId());
    }

    @Test
    void shouldConfirmContract() throws Exception {
        // First initiate via service (direct DB)
        ContractInstance instance = createInitiatedInstance();
        LocalDateTime beforeConfirm = instance.getUpdatedAt();

        mockMvc.perform(put("/api/contracts/" + instance.getId() + "/confirm")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractStatus").value("CONFIRMED"));

        // Verify via repo that updatedAt moved forward
        ContractInstance updated = instanceRepository.findById(instance.getId()).orElseThrow();
        assertThat(updated.getContractStatus()).isEqualTo("CONFIRMED");
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(beforeConfirm);
    }

    @Test
    void shouldRenewSignedContract() throws Exception {
        ContractInstance signed = createSignedInstance();

        Map<String, String> body = Map.of("newEndDate", "2099-01-01");

        mockMvc.perform(put("/api/contracts/" + signed.getId() + "/renew")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractStatus").value("RENEWED"))
                .andExpect(jsonPath("$.endDate").value("2099-01-01"));

        // Verify DB row updated
        ContractInstance updated = instanceRepository.findById(signed.getId()).orElseThrow();
        assertThat(updated.getEndDate()).isEqualTo(LocalDate.of(2099, 1, 1));
    }

    @Test
    void shouldTerminateAsReviewer() throws Exception {
        ContractInstance active = createActiveInstance();

        mockMvc.perform(put("/api/contracts/" + active.getId() + "/terminate")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractStatus").value("TERMINATED"));

        ContractInstance updated = instanceRepository.findById(active.getId()).orElseThrow();
        assertThat(updated.getContractStatus()).isEqualTo("TERMINATED");
    }

    @Test
    void shouldVoidAsAdmin() throws Exception {
        ContractInstance initiated = createInitiatedInstance();

        mockMvc.perform(put("/api/contracts/" + initiated.getId() + "/void")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractStatus").value("VOIDED"));

        ContractInstance updated = instanceRepository.findById(initiated.getId()).orElseThrow();
        assertThat(updated.getContractStatus()).isEqualTo("VOIDED");
    }

    @Test
    void shouldRejectVoidAsUser() throws Exception {
        ContractInstance initiated = createInitiatedInstance();

        mockMvc.perform(put("/api/contracts/" + initiated.getId() + "/void")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldGetMyContracts() throws Exception {
        createInitiatedInstance(); // ensures at least one contract linked to regularUser

        mockMvc.perform(get("/api/contracts/my")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldGetPrintableContract() throws Exception {
        ContractInstance instance = createInitiatedInstance();

        mockMvc.perform(get("/api/contracts/" + instance.getId() + "/print")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instance.getId()))
                .andExpect(jsonPath("$.renderedContent").exists());
    }

    // =========================================================================
    // Added tests
    // =========================================================================

    @Test
    void shouldReturn404ForMissingContract() throws Exception {
        mockMvc.perform(get("/api/contracts/99999")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectConfirmOutsideInitiatedState() throws Exception {
        // Create a contract already in CONFIRMED state
        ContractInstance confirmed = createInitiatedInstance();
        confirmed.setContractStatus("CONFIRMED");
        instanceRepository.save(confirmed);

        // Try to confirm again — service will reject because status is not INITIATED
        MvcResult result = mockMvc.perform(put("/api/contracts/" + confirmed.getId() + "/confirm")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isConflict())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).isNotBlank();
    }

    @Test
    void shouldReturnContractInstanceOnPrint() throws Exception {
        ContractInstance instance = createInitiatedInstance();

        mockMvc.perform(get("/api/contracts/" + instance.getId() + "/print")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renderedContent").exists())
                .andExpect(jsonPath("$.id").value(instance.getId()));
    }

    // =========================================================================
    // Evidence endpoint tests
    // =========================================================================

    @Test
    void shouldRejectEvidenceUploadWithInvalidFileType() throws Exception {
        ContractInstance instance = createInitiatedInstance();

        // .txt file — extension not in allowed list → StorageService throws BusinessRuleException → 409
        MockMultipartFile txtFile = new MockMultipartFile(
                "file", "document.txt", "text/plain", "hello world".getBytes());

        mockMvc.perform(multipart("/api/contracts/" + instance.getId() + "/evidence")
                .file(txtFile)
                .with(csrf())
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectEvidenceUploadMissingContract() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evidence.jpg", "image/jpeg", MIN_JPEG);

        mockMvc.perform(multipart("/api/contracts/99999/evidence")
                .file(file)
                .with(csrf())
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListEvidenceForOwnerAndReturnEmptyInitially() throws Exception {
        ContractInstance instance = createInitiatedInstance();

        mockMvc.perform(get("/api/contracts/" + instance.getId() + "/evidence")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldDenyEvidenceListingToStranger() throws Exception {
        ContractInstance instance = createInitiatedInstance();

        long nonce = System.nanoTime();
        Role userRole = findOrCreateRole("ROLE_USER");
        User stranger = createUser("stranger_evlist_" + nonce, userRole);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(get("/api/contracts/" + instance.getId() + "/evidence")
                .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Sign endpoint tests
    // =========================================================================

    @Test
    void shouldRejectSigningMissingFile() throws Exception {
        ContractInstance instance = createInitiatedInstance();
        // Advance to CONFIRMED so the only rejection is the missing file
        instance.setContractStatus("CONFIRMED");
        instanceRepository.save(instance);

        // No "file" part attached — Spring will fail to bind the @RequestPart
        int status = mockMvc.perform(multipart(HttpMethod.PUT,
                "/api/contracts/" + instance.getId() + "/sign")
                .param("signatureType", "DRAWN")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getStatus();

        // Missing required @RequestPart → 400 or 500 (documented: either is acceptable)
        assertThat(status).isIn(400, 500);
    }

    @Test
    void shouldRejectRepeatSigningAfterSigned() throws Exception {
        // Use a contract that is already SIGNED — service requireStatus(CONFIRMED) will fail
        ContractInstance signed = createSignedInstance();

        MockMultipartFile sigFile = new MockMultipartFile(
                "file", "signature.png", "image/png", MIN_PNG);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/contracts/" + signed.getId() + "/sign")
                .file(sigFile)
                .param("signatureType", "DRAWN")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldAllowSigningOnlyAsContractOwner() throws Exception {
        ContractInstance instance = createInitiatedInstance();
        instance.setContractStatus("CONFIRMED");
        instanceRepository.save(instance);

        MockMultipartFile sigFile = new MockMultipartFile(
                "file", "signature.png", "image/png", MIN_PNG);

        // reviewerUser is not the contract userId — should be forbidden
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/contracts/" + instance.getId() + "/sign")
                .file(sigFile)
                .param("signatureType", "DRAWN")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Terminate endpoint tests
    // =========================================================================

    @Test
    void shouldRejectTerminatingInitiatedContract() throws Exception {
        ContractInstance instance = createInitiatedInstance();

        // INITIATED state is not ACTIVE/SIGNED/RENEWED → service throws BusinessRuleException → 409
        mockMvc.perform(put("/api/contracts/" + instance.getId() + "/terminate")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectTerminateAsRegularUser() throws Exception {
        ContractInstance instance = createActiveInstance();

        // USER role is not ADMIN or REVIEWER — @PreAuthorize rejects → 403
        mockMvc.perform(put("/api/contracts/" + instance.getId() + "/terminate")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Renew endpoint tests
    // =========================================================================

    @Test
    void shouldRejectRenewingUnsignedContract() throws Exception {
        ContractInstance instance = createInitiatedInstance();

        Map<String, String> body = Map.of("newEndDate", "2099-01-01");

        // INITIATED state — service requires ACTIVE or SIGNED → 409
        mockMvc.perform(put("/api/contracts/" + instance.getId() + "/renew")
                .with(csrf())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectRenewAsStranger() throws Exception {
        ContractInstance signed = createSignedInstance();

        long nonce = System.nanoTime();
        Role userRole = findOrCreateRole("ROLE_USER");
        User stranger = createUser("stranger_renew_" + nonce, userRole);
        String strangerTok = jwtService.generateAccessToken(stranger);

        Map<String, String> body = Map.of("newEndDate", "2099-01-01");

        // Stranger has no access to contract (not userId, not reviewerId, not staff) → 403
        mockMvc.perform(put("/api/contracts/" + signed.getId() + "/renew")
                .with(csrf())
                .header("Authorization", "Bearer " + strangerTok)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Archive endpoint tests
    // =========================================================================

    @Test
    void shouldRejectArchivingNonSignedContract() throws Exception {
        ContractInstance instance = createActiveInstance();

        // archiveContract requires SIGNED status; ACTIVE throws BusinessRuleException → 409
        mockMvc.perform(put("/api/contracts/" + instance.getId() + "/archive")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectArchiveAsReviewer() throws Exception {
        ContractInstance signed = createSignedInstance();

        // @PreAuthorize("hasRole('ADMIN')") — reviewer is not admin → 403
        mockMvc.perform(put("/api/contracts/" + signed.getId() + "/archive")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // GET /api/contracts/{id} tests
    // =========================================================================

    @Test
    void shouldReturn404ForNonexistentContract() throws Exception {
        mockMvc.perform(get("/api/contracts/99999")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnDetailedContractForOwnerWithFields() throws Exception {
        ContractInstance instance = createInitiatedInstance();

        mockMvc.perform(get("/api/contracts/" + instance.getId())
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instance.getId()))
                .andExpect(jsonPath("$.contractStatus").exists())
                .andExpect(jsonPath("$.startDate").exists())
                .andExpect(jsonPath("$.endDate").exists());
    }

    @Test
    void shouldReturnDetailedContractForStaffAdmin() throws Exception {
        ContractInstance instance = createInitiatedInstance();

        mockMvc.perform(get("/api/contracts/" + instance.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instance.getId()))
                .andExpect(jsonPath("$.contractStatus").exists())
                .andExpect(jsonPath("$.orderId").value(acceptedOrderId))
                .andExpect(jsonPath("$.userId").value(regularUser.getId()))
                .andExpect(jsonPath("$.reviewerId").value(reviewerUser.getId()))
                .andExpect(jsonPath("$.startDate").exists())
                .andExpect(jsonPath("$.endDate").exists());
    }

    @Test
    void shouldReturnDetailedContractForAssignedReviewer() throws Exception {
        // reviewerUser is the assigned reviewer on the contract — should be allowed access
        ContractInstance instance = createInitiatedInstance();

        mockMvc.perform(get("/api/contracts/" + instance.getId())
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instance.getId()))
                .andExpect(jsonPath("$.contractStatus").exists());
    }

    @Test
    void shouldDenyDetailForUnassignedStranger() throws Exception {
        ContractInstance instance = createInitiatedInstance();

        // Create a second user who is neither the contract owner nor the assigned reviewer
        long nonce = System.nanoTime();
        Role userRole = findOrCreateRole("ROLE_USER");
        User stranger = createUser("stranger_detail_" + nonce, userRole);
        String strangerToken = jwtService.generateAccessToken(stranger);

        // BusinessRuleException("Access denied to contract") → 403
        mockMvc.perform(get("/api/contracts/" + instance.getId())
                .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldIncludeSignatureRelatedFieldsInPayload() throws Exception {
        // Use a SIGNED contract so signedAt and contractStatus=SIGNED are present
        ContractInstance signed = createSignedInstance();

        mockMvc.perform(get("/api/contracts/" + signed.getId())
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedAt").exists())
                .andExpect(jsonPath("$.contractStatus").value("SIGNED"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ContractInstance createInitiatedInstance() {
        ContractInstance instance = new ContractInstance();
        instance.setOrderId(acceptedOrderId);
        instance.setTemplateVersionId(versionId);
        instance.setUserId(regularUser.getId());
        instance.setReviewerId(reviewerUser.getId());
        instance.setContractStatus("INITIATED");
        instance.setRenderedContent("Rendered contract");
        instance.setFieldValues("{}");
        instance.setStartDate(LocalDate.of(2099, 1, 1));
        instance.setEndDate(LocalDate.of(2099, 12, 31));
        instance.setCreatedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now().minusSeconds(1));
        return instanceRepository.save(instance);
    }

    private ContractInstance createSignedInstance() {
        ContractInstance instance = new ContractInstance();
        instance.setOrderId(acceptedOrderId);
        instance.setTemplateVersionId(versionId);
        instance.setUserId(regularUser.getId());
        instance.setReviewerId(reviewerUser.getId());
        instance.setContractStatus("SIGNED");
        instance.setRenderedContent("Signed contract");
        instance.setFieldValues("{}");
        instance.setStartDate(LocalDate.of(2090, 1, 1));
        instance.setEndDate(LocalDate.of(2099, 12, 31));
        instance.setSignedAt(LocalDateTime.now());
        instance.setCreatedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        return instanceRepository.save(instance);
    }

    private ContractInstance createActiveInstance() {
        ContractInstance instance = new ContractInstance();
        instance.setOrderId(acceptedOrderId);
        instance.setTemplateVersionId(versionId);
        instance.setUserId(regularUser.getId());
        instance.setReviewerId(reviewerUser.getId());
        instance.setContractStatus("ACTIVE");
        instance.setRenderedContent("Active contract");
        instance.setFieldValues("{}");
        instance.setStartDate(LocalDate.of(2090, 1, 1));
        instance.setEndDate(LocalDate.of(2099, 12, 31));
        instance.setCreatedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        return instanceRepository.save(instance);
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
