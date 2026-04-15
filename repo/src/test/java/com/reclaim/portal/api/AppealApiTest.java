package com.reclaim.portal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.appeals.repository.AppealRepository;
import com.reclaim.portal.appeals.repository.ArbitrationOutcomeRepository;
import com.reclaim.portal.appeals.repository.EvidenceFileRepository;
import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.contracts.entity.ContractInstance;
import com.reclaim.portal.contracts.repository.ContractInstanceRepository;
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
class AppealApiTest {

    // Minimal JPEG SOI + APP0 marker (same bytes as StorageServiceIntegrationTest)
    private static final byte[] MINIMAL_JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
        0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00,
        0x00, 0x01, 0x00, 0x01, 0x00, 0x00
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ContractInstanceRepository contractInstanceRepository;
    @Autowired private ContractTemplateRepository templateRepository;
    @Autowired private ContractTemplateVersionRepository versionRepository;
    @Autowired private AppealRepository appealRepository;
    @Autowired private EvidenceFileRepository evidenceFileRepository;
    @Autowired private ArbitrationOutcomeRepository arbitrationOutcomeRepository;

    private User appellantUser;
    private User otherUser;
    private User reviewerUser;

    private String appellantToken;
    private String otherUserToken;
    private String reviewerToken;

    private Long orderId;
    private Long contractId;
    // order/contract that belongs to otherUser
    private Long otherOrderId;
    private Long otherContractId;

    @BeforeEach
    void setUp() {
        long nonce = System.nanoTime();

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");

        appellantUser = createUser("appeal_user_"    + nonce, userRole);
        otherUser     = createUser("appeal_other_"   + nonce, userRole);
        reviewerUser  = createUser("appeal_reviewer_" + nonce, reviewerRole);

        appellantToken  = jwtService.generateAccessToken(appellantUser);
        otherUserToken  = jwtService.generateAccessToken(otherUser);
        reviewerToken   = jwtService.generateAccessToken(reviewerUser);

        // Create appointment
        Appointment appt = buildAppointment();
        appt = appointmentRepository.save(appt);

        // Order owned by appellantUser
        Order order = buildOrder(appellantUser.getId(), appt.getId());
        order = orderRepository.save(order);
        orderId = order.getId();

        // ContractInstance for that order
        ContractInstance contract = buildContract(orderId, appellantUser.getId());
        contract = contractInstanceRepository.save(contract);
        contractId = contract.getId();

        // Order + contract owned by otherUser (for cross-order test)
        Order otherOrder = buildOrder(otherUser.getId(), appt.getId());
        otherOrder = orderRepository.save(otherOrder);
        otherOrderId = otherOrder.getId();

        ContractInstance otherContract = buildContract(otherOrderId, otherUser.getId());
        otherContract = contractInstanceRepository.save(otherContract);
        otherContractId = otherContract.getId();
    }

    @Test
    void shouldCreateAppealAsOwner() throws Exception {
        Map<String, Object> body = Map.of(
                "orderId", orderId,
                "contractId", contractId,
                "reason", "I disagree with the assessment"
        );

        MvcResult result = mockMvc.perform(post("/api/appeals")
                .with(csrf())
                .header("Authorization", "Bearer " + appellantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.appealStatus").value("OPEN"))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andReturn();

        long appealId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
        // Verify appeal row exists in repository
        assertThat(appealRepository.existsById(appealId)).isTrue();
    }

    @Test
    void shouldRejectAppealWithUnrelatedContract() throws Exception {
        // appellantUser uses their own orderId but passes contractId from otherUser's order
        Map<String, Object> body = Map.of(
                "orderId", orderId,
                "contractId", otherContractId,
                "reason", "Trying invalid cross-reference"
        );

        mockMvc.perform(post("/api/appeals")
                .with(csrf())
                .header("Authorization", "Bearer " + appellantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldDenyAppealCreationForWrongOrderOwner() throws Exception {
        // otherUser tries to appeal appellantUser's order
        Map<String, Object> body = Map.of(
                "orderId", orderId,
                "contractId", contractId,
                "reason", "Not my order"
        );

        mockMvc.perform(post("/api/appeals")
                .with(csrf())
                .header("Authorization", "Bearer " + otherUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden()); // BusinessRuleException "Access denied" → 403
    }

    @Test
    void shouldUploadEvidenceAsAppellant() throws Exception {
        // First create an appeal
        Long appealId = createAppeal(appellantToken, orderId, contractId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "evidence.jpg", "image/jpeg", MINIMAL_JPEG);

        mockMvc.perform(multipart("/api/appeals/" + appealId + "/evidence")
                .file(file)
                .with(csrf())
                .header("Authorization", "Bearer " + appellantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").exists())
                .andExpect(jsonPath("$.filePath").exists())
                .andExpect(jsonPath("$.checksum").exists());

        // Verify evidence file row links to appeal
        var evidenceFiles = evidenceFileRepository.findByEntityTypeAndEntityId("APPEAL", appealId);
        assertThat(evidenceFiles).isNotEmpty();
    }

    @Test
    void shouldResolveAppealAsReviewer() throws Exception {
        Long appealId = createAppeal(appellantToken, orderId, contractId);

        Map<String, String> body = Map.of(
                "outcome", "APPROVED",
                "reasoning", "The appeal is valid"
        );

        mockMvc.perform(put("/api/appeals/" + appealId + "/resolve")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appealStatus").value("RESOLVED"));

        // Verify arbitration outcome exists
        assertThat(arbitrationOutcomeRepository.findByAppealId(appealId)).isPresent();
    }

    @Test
    void shouldRejectResolveAsUser() throws Exception {
        Long appealId = createAppeal(appellantToken, orderId, contractId);

        Map<String, String> body = Map.of("outcome", "APPROVED", "reasoning", "OK");

        mockMvc.perform(put("/api/appeals/" + appealId + "/resolve")
                .with(csrf())
                .header("Authorization", "Bearer " + appellantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldListMyAppeals() throws Exception {
        createAppeal(appellantToken, orderId, contractId);

        mockMvc.perform(get("/api/appeals/my")
                .header("Authorization", "Bearer " + appellantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldGetAppealDetailAsOwner() throws Exception {
        Long appealId = createAppeal(appellantToken, orderId, contractId);

        mockMvc.perform(get("/api/appeals/" + appealId)
                .header("Authorization", "Bearer " + appellantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appeal").exists())
                .andExpect(jsonPath("$.appeal.id").value(appealId))
                .andExpect(jsonPath("$.evidence").isArray());
    }

    // =========================================================================
    // GET /api/appeals/{id} additional tests
    // =========================================================================

    @Test
    void shouldReturn404ForNonexistentAppeal() throws Exception {
        mockMvc.perform(get("/api/appeals/99999")
                .header("Authorization", "Bearer " + appellantToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnAppealDetailForReviewer() throws Exception {
        Long appealId = createAppeal(appellantToken, orderId, contractId);

        mockMvc.perform(get("/api/appeals/" + appealId)
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appeal.id").value(appealId))
                .andExpect(jsonPath("$.evidence").isArray());
    }

    @Test
    void shouldReturnAppealDetailForAdmin() throws Exception {
        // Create an admin user inside this test
        long nonce = System.nanoTime();
        Role adminRole = findOrCreateRole("ROLE_ADMIN");
        User adminUser = createUser("appeal_admin_" + nonce, adminRole);
        String adminToken = jwtService.generateAccessToken(adminUser);

        Long appealId = createAppeal(appellantToken, orderId, contractId);

        mockMvc.perform(get("/api/appeals/" + appealId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appeal.id").value(appealId));
    }

    // =========================================================================
    // POST /api/appeals/{id}/evidence additional tests
    // =========================================================================

    @Test
    void shouldRejectEvidenceMissingAppeal() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evidence.jpg", "image/jpeg", MINIMAL_JPEG);

        mockMvc.perform(multipart("/api/appeals/99999/evidence")
                .file(file)
                .with(csrf())
                .header("Authorization", "Bearer " + appellantToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectEvidenceBadFileType() throws Exception {
        Long appealId = createAppeal(appellantToken, orderId, contractId);

        // .txt file → extension not allowed by StorageService → 409
        MockMultipartFile txtFile = new MockMultipartFile(
                "file", "doc.txt", "text/plain", "bad content".getBytes());

        mockMvc.perform(multipart("/api/appeals/" + appealId + "/evidence")
                .file(txtFile)
                .with(csrf())
                .header("Authorization", "Bearer " + appellantToken))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectEvidenceFromNonAppellant() throws Exception {
        Long appealId = createAppeal(appellantToken, orderId, contractId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "evidence.jpg", "image/jpeg", MINIMAL_JPEG);

        // otherUser is not the appellant and not staff → AccessDenied → 403
        mockMvc.perform(multipart("/api/appeals/" + appealId + "/evidence")
                .file(file)
                .with(csrf())
                .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // PUT /api/appeals/{id}/resolve additional tests
    // =========================================================================

    @Test
    void shouldRejectDuplicateResolution() throws Exception {
        Long appealId = createAppeal(appellantToken, orderId, contractId);

        Map<String, String> body = Map.of("outcome", "APPROVED", "reasoning", "First resolution");

        // First resolve — should succeed
        mockMvc.perform(put("/api/appeals/" + appealId + "/resolve")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body)))
                .andExpect(status().isOk());

        // Second resolve — appeal is now RESOLVED, not OPEN → 409
        mockMvc.perform(put("/api/appeals/" + appealId + "/resolve")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                        Map.of("outcome", "DENIED", "reasoning", "Second attempt"))))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectResolveOfNonexistentAppeal() throws Exception {
        Map<String, String> body = Map.of("outcome", "APPROVED", "reasoning", "test");

        mockMvc.perform(put("/api/appeals/99999/resolve")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAcceptResolveWithValidOutcomeAndReturnResolvedStatus() throws Exception {
        Long appealId = createAppeal(appellantToken, orderId, contractId);

        Map<String, String> body = Map.of("outcome", "DENIED", "reasoning", "Insufficient evidence");

        mockMvc.perform(put("/api/appeals/" + appealId + "/resolve")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appealStatus").value("RESOLVED"));
    }

    // =========================================================================
    // GET /api/appeals/{id} — nested payload depth tests
    // =========================================================================

    /**
     * Create an appeal + upload 2 evidence files + resolve with arbitration outcome.
     * Then GET /api/appeals/{id} as admin → 200.
     * Assert nested fields: appeal.id, appeal.appealStatus, evidence array of length 2,
     * outcome.outcome.
     */
    @Test
    void shouldReturnAppealDetailWithNestedEvidenceAndOutcomeForAdmin() throws Exception {
        // Create admin user
        long nonce = System.nanoTime();
        Role adminRole = findOrCreateRole("ROLE_ADMIN");
        User adminUser = createUser("appeal_nested_admin_" + nonce, adminRole);
        String adminToken = jwtService.generateAccessToken(adminUser);

        Long appealId = createAppeal(appellantToken, orderId, contractId);

        // Upload 2 evidence files
        for (int i = 0; i < 2; i++) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "evidence" + i + ".jpg", "image/jpeg", MINIMAL_JPEG);
            mockMvc.perform(multipart("/api/appeals/" + appealId + "/evidence")
                    .file(file)
                    .with(csrf())
                    .header("Authorization", "Bearer " + appellantToken))
                    .andExpect(status().isOk());
        }

        // Resolve with arbitration outcome
        Map<String, String> resolveBody = Map.of("outcome", "APPROVED", "reasoning", "Well supported");
        mockMvc.perform(put("/api/appeals/" + appealId + "/resolve")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resolveBody)))
                .andExpect(status().isOk());

        // GET /api/appeals/{id} as admin — assert nested payload shape
        mockMvc.perform(get("/api/appeals/" + appealId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appeal.id").value(appealId))
                .andExpect(jsonPath("$.appeal.appealStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.evidence").isArray())
                .andExpect(jsonPath("$.evidence.length()").value(2))
                .andExpect(jsonPath("$.outcome.outcome").exists());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Long createAppeal(String token, Long orderIdParam, Long contractIdParam) throws Exception {
        Map<String, Object> body = Map.of(
                "orderId", orderIdParam,
                "contractId", contractIdParam,
                "reason", "Test reason"
        );

        MvcResult result = mockMvc.perform(post("/api/appeals")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Appointment buildAppointment() {
        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(7));
        appt.setStartTime("10:00");
        appt.setEndTime("10:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(5);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        return appt;
    }

    private Order buildOrder(Long userId, Long apptId) {
        Order order = new Order();
        order.setUserId(userId);
        order.setAppointmentId(apptId);
        order.setOrderStatus("COMPLETED");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(BigDecimal.TEN);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    private ContractInstance buildContract(Long orderIdParam, Long userId) {
        ContractInstance contract = new ContractInstance();
        contract.setOrderId(orderIdParam);
        contract.setUserId(userId);
        contract.setContractStatus("INITIATED");
        contract.setRenderedContent("Sample contract");
        contract.setFieldValues("{}");
        contract.setStartDate(LocalDate.of(2099, 1, 1));
        contract.setEndDate(LocalDate.of(2099, 12, 31));
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        return contract;
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
