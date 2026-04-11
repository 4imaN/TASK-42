package com.reclaim.portal.reviewer.controller;

import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.contracts.entity.ContractInstance;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.entity.OrderItem;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.orders.service.OrderService;
import com.reclaim.portal.reviewer.service.ReviewerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reviewer")
@PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
public class ReviewerApiController {

    private final ReviewerService reviewerService;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Request body records
    // -------------------------------------------------------------------------

    record AdjustCategoryRequest(String newCategory) {}

    record AcceptOrderRequest(Long reviewerId) {}

    record ApproveCancelRequest(String reason) {}

    record InitiateContractRequest(Long templateVersionId, Long userId, String fieldValues,
                                   LocalDate startDate, LocalDate endDate) {}

    public ReviewerApiController(ReviewerService reviewerService,
                                 OrderRepository orderRepository,
                                 OrderService orderService,
                                 UserRepository userRepository) {
        this.reviewerService = reviewerService;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/reviewer/queue — returns PENDING_CONFIRMATION orders sorted by createdAt.
     */
    @GetMapping("/queue")
    public ResponseEntity<List<Order>> getQueue() {
        return ResponseEntity.ok(reviewerService.getQueue());
    }

    /**
     * PUT /api/reviewer/order-items/{id}/adjust-category — adjusts an item's category.
     */
    @PutMapping("/order-items/{id}/adjust-category")
    public ResponseEntity<OrderItem> adjustCategory(
            @PathVariable Long id,
            @RequestBody AdjustCategoryRequest req,
            Authentication auth) {
        Long reviewerId = resolveUserId(auth);
        return ResponseEntity.ok(reviewerService.adjustCategory(id, req.newCategory(), reviewerId));
    }

    /**
     * PUT /api/reviewer/orders/{id}/accept — reviewer accepts/assigns themselves to an order.
     */
    @PutMapping("/orders/{id}/accept")
    public ResponseEntity<Order> acceptOrder(@PathVariable Long id, Authentication auth) {
        Long reviewerId = resolveUserId(auth);
        return ResponseEntity.ok(orderService.acceptOrder(id, reviewerId));
    }

    /**
     * PUT /api/reviewer/orders/{id}/approve-cancel — reviewer approves a cancellation request.
     */
    @PutMapping("/orders/{id}/approve-cancel")
    public ResponseEntity<Order> approveCancel(
            @PathVariable Long id,
            @RequestBody ApproveCancelRequest req,
            Authentication auth) {
        Long reviewerId = resolveUserId(auth);
        return ResponseEntity.ok(orderService.approveCancellation(id, reviewerId, req.reason()));
    }

    /**
     * POST /api/reviewer/orders/{id}/initiate-contract — initiates a contract for an order.
     */
    @PostMapping("/orders/{id}/initiate-contract")
    public ResponseEntity<ContractInstance> initiateContract(
            @PathVariable Long id,
            @RequestBody InitiateContractRequest req,
            Authentication auth) {
        Long reviewerId = resolveUserId(auth);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order", id));
        ContractInstance instance = reviewerService.initiateContractForOrder(
                id, req.templateVersionId(), order.getUserId(),
                reviewerId, req.fieldValues(), req.startDate(), req.endDate());
        return ResponseEntity.ok(instance);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Long resolveUserId(Authentication auth) {
        String username = ((UserDetails) auth.getPrincipal()).getUsername();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    }
}
