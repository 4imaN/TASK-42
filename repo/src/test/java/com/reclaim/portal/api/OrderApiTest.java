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
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    @Autowired
    private OrderRepository orderRepository;

    private String accessToken;
    private Long userId;
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
        userId = user.getId();

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
     * Response should contain orderStatus, userId, and appointmentId.
     */
    @Test
    void createOrderAcceptsCorrectJsonShape() throws Exception {
        Map<String, Object> requestBody = Map.of(
            "itemIds", List.of(itemId),
            "appointmentId", appointmentId,
            "appointmentType", "PICKUP"
        );

        MvcResult result = mockMvc.perform(post("/api/orders")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.orderStatus").value("PENDING_CONFIRMATION"))
               .andExpect(jsonPath("$.userId").isNumber())
               .andExpect(jsonPath("$.appointmentId").value(appointmentId))
               .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("userId").asLong()).isEqualTo(userId);
    }

    /**
     * POST /api/orders with a missing appointmentId field should result in a 4xx response
     * (NPE or validation error from the service layer), confirming that
     * appointmentId is a required part of the JSON contract.
     *
     * When appointmentId is omitted, the OrderApiController record has
     * @NotNull which triggers 400 Bad Request via MethodArgumentNotValidException.
     */
    @Test
    void createOrderRejectsMissingAppointmentId() throws Exception {
        // appointmentId is null — @NotNull on the record field triggers 400
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

        // @NotNull on appointmentId in the CreateOrderRequest record → 400 Bad Request
        assertThat(status).isEqualTo(400);
    }

    /**
     * POST /api/orders with an empty itemIds list should fail with a business rule error (400).
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

    // =========================================================================
    // GET /api/orders/my
    // =========================================================================

    /**
     * Verifies that GET /api/orders/my returns only orders for the authenticated user,
     * not orders belonging to a different user.
     */
    @Test
    void shouldReturnOnlyMyOrdersNotOthers() throws Exception {
        long nonce = System.nanoTime();

        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_USER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        // Create userB
        User userB = new User();
        userB.setUsername("orderapi_userB_" + nonce);
        userB.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        userB.setEmail("orderapi_b_" + nonce + "@example.com");
        userB.setEnabled(true);
        userB.setLocked(false);
        userB.setForcePasswordReset(false);
        userB.setFailedAttempts(0);
        userB.setCreatedAt(LocalDateTime.now());
        userB.setUpdatedAt(LocalDateTime.now());
        userB.setRoles(new HashSet<>(Set.of(userRole)));
        userB = userRepository.save(userB);

        // Create appointment for orders
        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(4));
        apt.setStartTime("11:00");
        apt.setEndTime("11:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(10);
        apt.setSlotsBooked(0);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        // Create an order for userA (the current test user)
        Order orderA = new Order();
        orderA.setUserId(userId);
        orderA.setAppointmentId(apt.getId());
        orderA.setOrderStatus("PENDING_CONFIRMATION");
        orderA.setAppointmentType("PICKUP");
        orderA.setRescheduleCount(0);
        orderA.setCurrency("USD");
        orderA.setTotalPrice(new BigDecimal("9.99"));
        orderA.setCreatedAt(LocalDateTime.now());
        orderA.setUpdatedAt(LocalDateTime.now());
        orderA = orderRepository.save(orderA);
        Long orderAId = orderA.getId();

        // Create an order for userB
        Order orderB = new Order();
        orderB.setUserId(userB.getId());
        orderB.setAppointmentId(apt.getId());
        orderB.setOrderStatus("PENDING_CONFIRMATION");
        orderB.setAppointmentType("PICKUP");
        orderB.setRescheduleCount(0);
        orderB.setCurrency("USD");
        orderB.setTotalPrice(new BigDecimal("9.99"));
        orderB.setCreatedAt(LocalDateTime.now());
        orderB.setUpdatedAt(LocalDateTime.now());
        orderB = orderRepository.save(orderB);
        Long orderBId = orderB.getId();

        // GET /api/orders/my as userA
        MvcResult result = mockMvc.perform(get("/api/orders/my")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();

        // userA's order must appear
        boolean foundA = false;
        boolean foundB = false;
        for (var node : body) {
            long id = node.get("id").asLong();
            if (id == orderAId) foundA = true;
            if (id == orderBId) foundB = true;
        }
        assertThat(foundA).as("userA's order must appear in /my").isTrue();
        assertThat(foundB).as("userB's order must NOT appear in /my for userA").isFalse();

        // Verify via repo that user's count matches
        List<Order> userAOrders = orderRepository.findByUserId(userId);
        assertThat((long) body.size()).isEqualTo(userAOrders.size());
    }

    /**
     * A fresh user with no orders should get a 200 with an empty array from GET /api/orders/my.
     */
    @Test
    void shouldReturnEmptyArrayWhenUserHasNoOrders() throws Exception {
        long nonce = System.nanoTime();

        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_USER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        User freshUser = new User();
        freshUser.setUsername("fresh_user_" + nonce);
        freshUser.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        freshUser.setEmail("fresh_" + nonce + "@example.com");
        freshUser.setEnabled(true);
        freshUser.setLocked(false);
        freshUser.setForcePasswordReset(false);
        freshUser.setFailedAttempts(0);
        freshUser.setCreatedAt(LocalDateTime.now());
        freshUser.setUpdatedAt(LocalDateTime.now());
        freshUser.setRoles(new HashSet<>(Set.of(userRole)));
        freshUser = userRepository.save(freshUser);
        String freshToken = jwtService.generateAccessToken(freshUser);

        mockMvc.perform(get("/api/orders/my")
                .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // =========================================================================
    // GET /api/orders/{id}
    // =========================================================================

    /**
     * GET /api/orders/99999 for a non-existent order should return 404.
     */
    @Test
    void shouldReturn404ForMissingOrder() throws Exception {
        mockMvc.perform(get("/api/orders/99999")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    /**
     * The order owner gets a 200 with nested order/items/logs structure.
     */
    @Test
    void shouldReturnOrderWithItemsAndLogsForOwner() throws Exception {
        // Create an order owned by the test user
        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(5));
        apt.setStartTime("09:00");
        apt.setEndTime("09:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(10);
        apt.setSlotsBooked(0);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        Order order = new Order();
        order.setUserId(userId);
        order.setAppointmentId(apt.getId());
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("9.99"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        Long orderId = order.getId();

        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.id").value(orderId))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.logs").isArray());
    }

    /**
     * A stranger (different user) attempting to read another user's order detail gets 403.
     */
    @Test
    void shouldDenyOrderDetailForStranger() throws Exception {
        long nonce = System.nanoTime();

        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_USER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        User stranger = new User();
        stranger.setUsername("stranger_order_" + nonce);
        stranger.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        stranger.setEmail("stranger_order_" + nonce + "@example.com");
        stranger.setEnabled(true);
        stranger.setLocked(false);
        stranger.setForcePasswordReset(false);
        stranger.setFailedAttempts(0);
        stranger.setCreatedAt(LocalDateTime.now());
        stranger.setUpdatedAt(LocalDateTime.now());
        stranger.setRoles(new HashSet<>(Set.of(userRole)));
        stranger = userRepository.save(stranger);
        String strangerToken = jwtService.generateAccessToken(stranger);

        // Order owned by the primary test user
        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(6));
        apt.setStartTime("14:00");
        apt.setEndTime("14:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(10);
        apt.setSlotsBooked(0);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        Order order = new Order();
        order.setUserId(userId);
        order.setAppointmentId(apt.getId());
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("9.99"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        mockMvc.perform(get("/api/orders/" + order.getId())
                .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    /**
     * A reviewer (staff) can access any order's detail even without being the owner.
     */
    @Test
    void shouldAllowOrderDetailForReviewer() throws Exception {
        long nonce = System.nanoTime();

        Role reviewerRole = roleRepository.findByName("ROLE_REVIEWER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_REVIEWER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        User reviewer = new User();
        reviewer.setUsername("reviewer_order_" + nonce);
        reviewer.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        reviewer.setEmail("reviewer_order_" + nonce + "@example.com");
        reviewer.setEnabled(true);
        reviewer.setLocked(false);
        reviewer.setForcePasswordReset(false);
        reviewer.setFailedAttempts(0);
        reviewer.setCreatedAt(LocalDateTime.now());
        reviewer.setUpdatedAt(LocalDateTime.now());
        reviewer.setRoles(new HashSet<>(Set.of(reviewerRole)));
        reviewer = userRepository.save(reviewer);
        String reviewerToken = jwtService.generateAccessToken(reviewer);

        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(7));
        apt.setStartTime("15:00");
        apt.setEndTime("15:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(10);
        apt.setSlotsBooked(0);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        Order order = new Order();
        order.setUserId(userId);
        order.setAppointmentId(apt.getId());
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("9.99"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        mockMvc.perform(get("/api/orders/" + order.getId())
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.id").value(order.getId()));
    }

    // =========================================================================
    // PUT /api/orders/{id}/reschedule
    // =========================================================================

    /**
     * Rescheduling to an appointment of a mismatched type returns 409
     * with a message containing "Cannot change appointment type".
     */
    @Test
    void shouldRejectRescheduleWithMismatchedAppointmentType() throws Exception {
        // Create a PICKUP appointment and an order using it
        Appointment pickupApt = new Appointment();
        pickupApt.setAppointmentDate(LocalDate.now().plusDays(5));
        pickupApt.setStartTime("10:00");
        pickupApt.setEndTime("10:30");
        pickupApt.setAppointmentType("PICKUP");
        pickupApt.setSlotsAvailable(10);
        pickupApt.setSlotsBooked(1); // already booked once for the order
        pickupApt.setCreatedAt(LocalDateTime.now());
        pickupApt = appointmentRepository.save(pickupApt);

        // Create a DROPOFF appointment to try to reschedule to
        Appointment dropoffApt = new Appointment();
        dropoffApt.setAppointmentDate(LocalDate.now().plusDays(6));
        dropoffApt.setStartTime("10:00");
        dropoffApt.setEndTime("10:30");
        dropoffApt.setAppointmentType("DROPOFF");
        dropoffApt.setSlotsAvailable(10);
        dropoffApt.setSlotsBooked(0);
        dropoffApt.setCreatedAt(LocalDateTime.now());
        dropoffApt = appointmentRepository.save(dropoffApt);

        Order order = new Order();
        order.setUserId(userId);
        order.setAppointmentId(pickupApt.getId());
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("9.99"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        Map<String, Long> body = Map.of("newAppointmentId", dropoffApt.getId());

        MvcResult result = mockMvc.perform(put("/api/orders/" + order.getId() + "/reschedule")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).containsIgnoringCase("Cannot change appointment type");
    }

    /**
     * An order that has already been rescheduled twice cannot be rescheduled again.
     */
    @Test
    void shouldRejectThirdReschedule() throws Exception {
        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(5));
        apt.setStartTime("10:00");
        apt.setEndTime("10:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(10);
        apt.setSlotsBooked(1);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        Appointment newApt = new Appointment();
        newApt.setAppointmentDate(LocalDate.now().plusDays(8));
        newApt.setStartTime("10:00");
        newApt.setEndTime("10:30");
        newApt.setAppointmentType("PICKUP");
        newApt.setSlotsAvailable(10);
        newApt.setSlotsBooked(0);
        newApt.setCreatedAt(LocalDateTime.now());
        newApt = appointmentRepository.save(newApt);

        // Seed order with rescheduleCount=2 directly via repository
        Order order = new Order();
        order.setUserId(userId);
        order.setAppointmentId(apt.getId());
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(2); // already at max
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("9.99"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        Map<String, Long> body = Map.of("newAppointmentId", newApt.getId());

        MvcResult result = mockMvc.perform(put("/api/orders/" + order.getId() + "/reschedule")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).containsAnyOf("maximum", "rescheduled");
    }

    /**
     * Passing a non-existent appointmentId to reschedule returns 404 or 409
     * depending on whether AppointmentService throws EntityNotFoundException or BusinessRuleException.
     */
    @Test
    void shouldRejectRescheduleForMissingAppointment() throws Exception {
        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(5));
        apt.setStartTime("10:00");
        apt.setEndTime("10:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(10);
        apt.setSlotsBooked(1);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        Order order = new Order();
        order.setUserId(userId);
        order.setAppointmentId(apt.getId());
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("9.99"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        Map<String, Long> body = Map.of("newAppointmentId", 99999L);

        // EntityNotFoundException → 404; if wrapped it may be 409 — accept either
        int status = mockMvc.perform(put("/api/orders/" + order.getId() + "/reschedule")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(404, 409);
    }

    /**
     * A non-owner (stranger) attempting to reschedule another user's order gets 403.
     */
    @Test
    void shouldRejectRescheduleAsStranger() throws Exception {
        long nonce = System.nanoTime();

        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_USER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        User stranger = new User();
        stranger.setUsername("reschedule_stranger_" + nonce);
        stranger.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        stranger.setEmail("reschedule_stranger_" + nonce + "@example.com");
        stranger.setEnabled(true);
        stranger.setLocked(false);
        stranger.setForcePasswordReset(false);
        stranger.setFailedAttempts(0);
        stranger.setCreatedAt(LocalDateTime.now());
        stranger.setUpdatedAt(LocalDateTime.now());
        stranger.setRoles(new HashSet<>(Set.of(userRole)));
        stranger = userRepository.save(stranger);
        String strangerToken = jwtService.generateAccessToken(stranger);

        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(5));
        apt.setStartTime("10:00");
        apt.setEndTime("10:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(10);
        apt.setSlotsBooked(1);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        Appointment newApt = new Appointment();
        newApt.setAppointmentDate(LocalDate.now().plusDays(9));
        newApt.setStartTime("10:00");
        newApt.setEndTime("10:30");
        newApt.setAppointmentType("PICKUP");
        newApt.setSlotsAvailable(10);
        newApt.setSlotsBooked(0);
        newApt.setCreatedAt(LocalDateTime.now());
        newApt = appointmentRepository.save(newApt);

        Order order = new Order();
        order.setUserId(userId); // owned by primary test user, not stranger
        order.setAppointmentId(apt.getId());
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("9.99"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        Map<String, Long> body = Map.of("newAppointmentId", newApt.getId());

        mockMvc.perform(put("/api/orders/" + order.getId() + "/reschedule")
                .with(csrf())
                .header("Authorization", "Bearer " + strangerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // PUT /api/orders/{id}/approve-cancel
    // =========================================================================

    /**
     * Calling approve-cancel on an order in PENDING_CONFIRMATION (not EXCEPTION) returns 409.
     */
    @Test
    void shouldRejectApproveCancelOnWrongState() throws Exception {
        long nonce = System.nanoTime();

        Role reviewerRole = roleRepository.findByName("ROLE_REVIEWER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_REVIEWER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        User reviewer = new User();
        reviewer.setUsername("approve_reviewer_" + nonce);
        reviewer.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        reviewer.setEmail("approve_reviewer_" + nonce + "@example.com");
        reviewer.setEnabled(true);
        reviewer.setLocked(false);
        reviewer.setForcePasswordReset(false);
        reviewer.setFailedAttempts(0);
        reviewer.setCreatedAt(LocalDateTime.now());
        reviewer.setUpdatedAt(LocalDateTime.now());
        reviewer.setRoles(new HashSet<>(Set.of(reviewerRole)));
        reviewer = userRepository.save(reviewer);
        String reviewerToken = jwtService.generateAccessToken(reviewer);

        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(5));
        apt.setStartTime("10:00");
        apt.setEndTime("10:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(10);
        apt.setSlotsBooked(1);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        Order order = new Order();
        order.setUserId(userId);
        order.setAppointmentId(apt.getId());
        order.setOrderStatus("PENDING_CONFIRMATION"); // wrong state — must be EXCEPTION
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("9.99"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        Map<String, String> body = Map.of("reason", "Test reason");

        mockMvc.perform(put("/api/orders/" + order.getId() + "/approve-cancel")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    /**
     * A regular user (not reviewer/admin) attempting approve-cancel is rejected with 403
     * by the @PreAuthorize role guard.
     */
    @Test
    void shouldRejectApproveCancelForRegularUser() throws Exception {
        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(5));
        apt.setStartTime("10:00");
        apt.setEndTime("10:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(10);
        apt.setSlotsBooked(1);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        Order order = new Order();
        order.setUserId(userId);
        order.setAppointmentId(apt.getId());
        order.setOrderStatus("EXCEPTION");
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("9.99"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        Map<String, String> body = Map.of("reason", "Test reason");

        // Regular user (ROLE_USER only) → @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')") → 403
        mockMvc.perform(put("/api/orders/" + order.getId() + "/approve-cancel")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    /**
     * Sets order to EXCEPTION state, then reviewer calls approve-cancel with reason "Legitimate reason".
     * Verifies persisted fields: orderStatus == CANCELED, cancellationApprovedBy == reviewerId,
     * cancellationReason contains "Legitimate reason".
     */
    @Test
    void shouldPersistCancellationOutcome() throws Exception {
        long nonce = System.nanoTime();

        Role reviewerRole = roleRepository.findByName("ROLE_REVIEWER").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_REVIEWER"); r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        User reviewer = new User();
        reviewer.setUsername("persist_reviewer_" + nonce);
        reviewer.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        reviewer.setEmail("persist_reviewer_" + nonce + "@example.com");
        reviewer.setEnabled(true);
        reviewer.setLocked(false);
        reviewer.setForcePasswordReset(false);
        reviewer.setFailedAttempts(0);
        reviewer.setCreatedAt(LocalDateTime.now());
        reviewer.setUpdatedAt(LocalDateTime.now());
        reviewer.setRoles(new HashSet<>(Set.of(reviewerRole)));
        reviewer = userRepository.save(reviewer);
        String reviewerToken = jwtService.generateAccessToken(reviewer);
        Long reviewerId = reviewer.getId();

        Appointment apt = new Appointment();
        apt.setAppointmentDate(LocalDate.now().plusDays(5));
        apt.setStartTime("10:00");
        apt.setEndTime("10:30");
        apt.setAppointmentType("PICKUP");
        apt.setSlotsAvailable(10);
        apt.setSlotsBooked(1);
        apt.setCreatedAt(LocalDateTime.now());
        apt = appointmentRepository.save(apt);

        Order order = new Order();
        order.setUserId(userId);
        order.setAppointmentId(apt.getId());
        order.setOrderStatus("EXCEPTION"); // prerequisite state
        order.setAppointmentType("PICKUP");
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setTotalPrice(new BigDecimal("9.99"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        Long orderId = order.getId();

        Map<String, String> body = Map.of("reason", "Legitimate reason");

        mockMvc.perform(put("/api/orders/" + orderId + "/approve-cancel")
                .with(csrf())
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        // Verify persisted state via repository
        Order updated = orderRepository.findById(orderId).orElseThrow();
        assertThat(updated.getOrderStatus()).isEqualTo("CANCELED");
        assertThat(updated.getCancellationApprovedBy()).isEqualTo(reviewerId);
        assertThat(updated.getCancellationReason()).contains("Legitimate reason");
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
}
