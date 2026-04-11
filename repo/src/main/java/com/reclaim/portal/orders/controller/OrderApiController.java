package com.reclaim.portal.orders.controller;

import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderApiController {

    // -------------------------------------------------------------------------
    // Request body record types
    // -------------------------------------------------------------------------
    public record CreateOrderRequest(
        @jakarta.validation.constraints.NotEmpty List<Long> itemIds,
        @jakarta.validation.constraints.NotNull Long appointmentId,
        @jakarta.validation.constraints.NotBlank String appointmentType
    ) {}
    public record CancelRequest(@jakarta.validation.constraints.NotBlank String reason) {}
    public record RescheduleRequest(Long newAppointmentId) {}
    public record ApproveCancelRequest(String reason) {}

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------
    private final OrderService orderService;
    private final UserRepository userRepository;

    public OrderApiController(OrderService orderService, UserRepository userRepository) {
        this.orderService = orderService;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // POST / — create order
    // -------------------------------------------------------------------------
    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request,
                                             Authentication authentication) {
        Long userId = resolveUserId(authentication);
        Order order = orderService.createOrder(
            userId, request.itemIds(), request.appointmentId(), request.appointmentType());
        return ResponseEntity.ok(order);
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/accept — accept order (reviewer/admin)
    // -------------------------------------------------------------------------
    @PutMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public ResponseEntity<Order> acceptOrder(@PathVariable Long id,
                                             Authentication authentication) {
        Long reviewerId = resolveUserId(authentication);
        Order order = orderService.acceptOrder(id, reviewerId);
        return ResponseEntity.ok(order);
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/complete — complete order
    // -------------------------------------------------------------------------
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public ResponseEntity<Order> completeOrder(@PathVariable Long id,
                                               Authentication authentication) {
        Long actorId = resolveUserId(authentication);
        Order order = orderService.completeOrder(id, actorId);
        return ResponseEntity.ok(order);
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/cancel — cancel order
    // -------------------------------------------------------------------------
    @PutMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable Long id,
                                             @Valid @RequestBody CancelRequest request,
                                             Authentication authentication) {
        Long actorId = resolveUserId(authentication);
        Order order = orderService.cancelOrder(id, actorId, request.reason());
        return ResponseEntity.ok(order);
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/approve-cancel — approve cancellation exception (admin/reviewer)
    // -------------------------------------------------------------------------
    @PutMapping("/{id}/approve-cancel")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public ResponseEntity<Order> approveCancellation(@PathVariable Long id,
                                                     @RequestBody ApproveCancelRequest request,
                                                     Authentication authentication) {
        Long reviewerId = resolveUserId(authentication);
        Order order = orderService.approveCancellation(id, reviewerId, request.reason());
        return ResponseEntity.ok(order);
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/reschedule — reschedule order
    // -------------------------------------------------------------------------
    @PutMapping("/{id}/reschedule")
    public ResponseEntity<Order> rescheduleOrder(@PathVariable Long id,
                                                  @RequestBody RescheduleRequest request,
                                                  Authentication authentication) {
        Long actorId = resolveUserId(authentication);
        Order order = orderService.rescheduleOrder(id, actorId, request.newAppointmentId());
        return ResponseEntity.ok(order);
    }

    // -------------------------------------------------------------------------
    // GET /my — orders for the authenticated user
    // -------------------------------------------------------------------------
    @GetMapping("/my")
    public ResponseEntity<List<Order>> getMyOrders(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        return ResponseEntity.ok(orderService.getOrdersByUser(userId));
    }

    // -------------------------------------------------------------------------
    // GET /{id} — order detail with logs
    // -------------------------------------------------------------------------
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable Long id,
                                                        Authentication authentication) {
        Long actorId = resolveUserId(authentication);
        boolean staff = isStaff(authentication);
        return ResponseEntity.ok(orderService.getOrderWithLogs(id, actorId, staff));
    }

    // -------------------------------------------------------------------------
    // Helper: extract User id from JWT principal
    // -------------------------------------------------------------------------
    private Long resolveUserId(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new EntityNotFoundException(
                "User not found: " + userDetails.getUsername()));
        return user.getId();
    }

    // -------------------------------------------------------------------------
    // Helper: check if the authenticated user has a staff role
    // -------------------------------------------------------------------------
    private boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_REVIEWER") || a.equals("ROLE_ADMIN"));
    }
}
