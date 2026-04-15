package com.reclaim.portal.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.contracts.entity.ContractClauseField;
import com.reclaim.portal.contracts.entity.ContractTemplate;
import com.reclaim.portal.contracts.entity.ContractTemplateVersion;
import com.reclaim.portal.contracts.repository.ContractClauseFieldRepository;
import com.reclaim.portal.contracts.repository.ContractInstanceRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateRepository;
import com.reclaim.portal.contracts.repository.ContractTemplateVersionRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

/**
 * Task 3: End-to-end MockMvc journey tests.
 *
 * NOT annotated with @Transactional so real DB state is visible between calls.
 * Data is unique per test run via System.nanoTime().
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserJourneyE2ETest {

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

    // Minimal valid JPEG bytes
    private static final byte[] MIN_JPEG = {
        (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0x00,0x10,0x4A,0x46,
        0x49,0x46,0x00,0x01,0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private RecyclingItemRepository recyclingItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ContractTemplateRepository contractTemplateRepository;
    @Autowired private ContractTemplateVersionRepository contractTemplateVersionRepository;
    @Autowired private ContractClauseFieldRepository contractClauseFieldRepository;
    @Autowired private ContractInstanceRepository contractInstanceRepository;

    // =========================================================================
    // Journey 1: User from search → review
    // =========================================================================

    @Test
    void userFromSearchToReviewJourney() throws Exception {
        long nonce = System.nanoTime();

        // ---- Setup ----
        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");

        User journeyUser = createUser("journey_user_" + nonce, "TestPassword1!", userRole);
        User reviewer    = createUser("journey_reviewer_" + nonce, "TestPassword1!", reviewerRole);

        String seededTitle = "JourneyWidget_" + nonce;

        RecyclingItem item = new RecyclingItem();
        item.setTitle(seededTitle);
        item.setNormalizedTitle(seededTitle.toLowerCase());
        item.setDescription("A widget for journey test");
        item.setCategory("Electronics");
        item.setItemCondition("GOOD");
        item.setPrice(new BigDecimal("19.99"));
        item.setCurrency("USD");
        item.setSellerId(journeyUser.getId());
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item = recyclingItemRepository.save(item);
        final Long seedItemId = item.getId();

        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(3));
        appt.setStartTime("10:00");
        appt.setEndTime("10:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(10);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);
        final Long apptId = appt.getId();

        // ---- Step 1: Login ----
        String userToken = loginAndGetToken(journeyUser.getUsername(), "TestPassword1!");

        // ---- Step 2: Search catalogue ----
        MvcResult searchResult = performGet("/api/catalog/search?keyword=" + seededTitle, userToken)
                .andExpect(status().isOk())
                .andReturn();
        JsonNode searchBody = objectMapper.readTree(searchResult.getResponse().getContentAsString());
        assertThat(searchBody.get("items").size()).isGreaterThan(0);

        long searchLogId = searchBody.get("searchLogId").asLong();
        long foundItemId = searchBody.get("items").get(0).get("id").asLong();

        // ---- Step 3: Track click ----
        performPost("/api/catalog/click", userToken,
                Map.of("itemId", foundItemId, "searchLogId", searchLogId))
                .andExpect(status().isOk());

        // ---- Step 4: Create order ----
        MvcResult orderResult = performPost("/api/orders", userToken,
                Map.of("itemIds", List.of(seedItemId),
                        "appointmentId", apptId,
                        "appointmentType", "PICKUP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.appointmentId").value(apptId))
                .andReturn();

        long orderId = objectMapper.readTree(orderResult.getResponse().getContentAsString())
                .get("id").asLong();

        // ---- Step 5: Reviewer accepts ----
        String reviewerToken = loginAndGetToken(reviewer.getUsername(), "TestPassword1!");

        performPut("/api/reviewer/orders/" + orderId + "/accept", reviewerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("ACCEPTED"));

        // ---- Step 6: Reviewer completes ----
        performPut("/api/orders/" + orderId + "/complete", reviewerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("COMPLETED"));

        // ---- Step 7: User creates review ----
        performPost("/api/reviews", userToken,
                Map.of("orderId", orderId, "rating", 5, "reviewText", "Great"))
                .andExpect(status().isOk());

        // ---- Step 8: User fetches own reviews ----
        MvcResult reviewsResult = performGet("/api/reviews/my", userToken)
                .andExpect(status().isOk())
                .andReturn();

        JsonNode reviews = objectMapper.readTree(reviewsResult.getResponse().getContentAsString());
        assertThat(reviews.isArray()).isTrue();
        boolean foundReview = false;
        for (JsonNode r : reviews) {
            if (r.get("orderId").asLong() == orderId) {
                foundReview = true;
                break;
            }
        }
        assertThat(foundReview).as("review for orderId %d should appear in my reviews", orderId).isTrue();
    }

    // =========================================================================
    // Journey 2: Contract initiation and sign
    // =========================================================================

    @Test
    void contractInitiationAndSignJourney() throws Exception {
        long nonce = System.nanoTime();

        // ---- Setup ----
        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");
        Role adminRole    = findOrCreateRole("ROLE_ADMIN");

        User owner    = createUser("csj_owner_"    + nonce, "TestPassword1!", userRole);
        User reviewer = createUser("csj_reviewer_" + nonce, "TestPassword1!", reviewerRole);
        User admin    = createUser("csj_admin_"    + nonce, "TestPassword1!", adminRole);

        Appointment appt = new Appointment();
        appt.setAppointmentDate(LocalDate.now().plusDays(4));
        appt.setStartTime("09:00");
        appt.setEndTime("09:30");
        appt.setAppointmentType("PICKUP");
        appt.setSlotsAvailable(10);
        appt.setSlotsBooked(0);
        appt.setCreatedAt(LocalDateTime.now());
        appt = appointmentRepository.save(appt);

        // Create ACCEPTED order directly in DB (shortcut to skip full order creation flow)
        Order order = new Order();
        order.setUserId(owner.getId());
        order.setAppointmentId(appt.getId());
        order.setOrderStatus("ACCEPTED");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(BigDecimal.TEN);
        order.setReviewerId(reviewer.getId());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        final long orderId = order.getId();

        // Contract template + version + clause field
        ContractTemplate template = new ContractTemplate();
        template.setName("CSJ Template " + nonce);
        template.setDescription("Contract sign journey template");
        template.setActive(true);
        template.setCreatedBy(admin.getId());
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        template = contractTemplateRepository.save(template);

        ContractTemplateVersion version = new ContractTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNumber(1);
        version.setContent("Contract for {{partyName}}");
        version.setChangeNotes("Initial");
        version.setCreatedBy(admin.getId());
        version.setCreatedAt(LocalDateTime.now());
        version = contractTemplateVersionRepository.save(version);
        final long templateVersionId = version.getId();

        ContractClauseField field = new ContractClauseField();
        field.setTemplateVersionId(version.getId());
        field.setFieldName("partyName");
        field.setFieldType("TEXT");
        field.setFieldLabel("Party Name");
        field.setRequired(true);
        field.setDefaultValue("Default Party");
        field.setDisplayOrder(1);
        contractClauseFieldRepository.save(field);

        String reviewerToken = loginAndGetToken(reviewer.getUsername(), "TestPassword1!");
        String ownerToken    = loginAndGetToken(owner.getUsername(), "TestPassword1!");
        String adminToken    = loginAndGetToken(admin.getUsername(), "TestPassword1!");

        // ---- Step 2: Reviewer initiates contract ----
        MvcResult initiateResult = performPost("/api/contracts", reviewerToken,
                Map.of("orderId", orderId,
                        "templateVersionId", templateVersionId,
                        "fieldValues", "{}",
                        "startDate", "2099-01-01",
                        "endDate", "2099-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractStatus").value("INITIATED"))
                .andReturn();

        long contractId = objectMapper.readTree(initiateResult.getResponse().getContentAsString())
                .get("id").asLong();

        // ---- Step 3: Reviewer confirms ----
        performPut("/api/contracts/" + contractId + "/confirm", reviewerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractStatus").value("CONFIRMED"));

        // ---- Step 4: Owner signs with PNG ----
        MockMultipartFile sigFile = new MockMultipartFile(
                "file", "signature.png", "image/png", MIN_PNG);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/contracts/" + contractId + "/sign")
                .file(sigFile)
                .param("signatureType", "DRAWN")
                .with(csrf())
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractStatus").value("SIGNED"));

        // ---- Step 5: Owner uploads evidence ----
        MockMultipartFile evidenceFile = new MockMultipartFile(
                "file", "evidence.jpg", "image/jpeg", MIN_JPEG);

        mockMvc.perform(multipart("/api/contracts/" + contractId + "/evidence")
                .file(evidenceFile)
                .with(csrf())
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").exists());

        // ---- Step 6: Owner lists contracts ----
        MvcResult myContractsResult = performGet("/api/contracts/my", ownerToken)
                .andExpect(status().isOk())
                .andReturn();

        JsonNode myContracts = objectMapper.readTree(myContractsResult.getResponse().getContentAsString());
        assertThat(myContracts.isArray()).isTrue();
        boolean found = false;
        for (JsonNode c : myContracts) {
            if (c.get("id").asLong() == contractId) {
                found = true;
                break;
            }
        }
        assertThat(found).as("contract %d should appear in my contracts", contractId).isTrue();

        // ---- Step 7: Admin archives ----
        performPut("/api/contracts/" + contractId + "/archive", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractStatus").value("ARCHIVED"));
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private String loginAndGetToken(String username, String password) throws Exception {
        Map<String, String> loginBody = Map.of("username", username, "password", password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private ResultActions performGet(String url, String token) throws Exception {
        return mockMvc.perform(get(url)
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions performPut(String url, String token) throws Exception {
        return mockMvc.perform(put(url)
                .with(csrf())
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions performPut(String url, String token, Object body) throws Exception {
        return mockMvc.perform(put(url)
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions performPost(String url, String token, Object body) throws Exception {
        return mockMvc.perform(post(url)
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Role findOrCreateRole(String name) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
    }

    private User createUser(String username, String password, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
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
