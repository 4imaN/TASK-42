package com.reclaim.portal.reviewer.service;

import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.contracts.entity.ContractInstance;
import com.reclaim.portal.contracts.service.ContractService;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.entity.OrderItem;
import com.reclaim.portal.orders.repository.OrderItemRepository;
import com.reclaim.portal.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ReviewerService {

    private static final String STATUS_PENDING_CONFIRMATION = "PENDING_CONFIRMATION";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ContractService contractService;

    public ReviewerService(OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           ContractService contractService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.contractService = contractService;
    }

    /**
     * Returns all orders in PENDING_CONFIRMATION status, ordered by createdAt ascending.
     */
    @Transactional(readOnly = true)
    public List<Order> getQueue() {
        return orderRepository.findByOrderStatus(STATUS_PENDING_CONFIRMATION)
                .stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return a.getCreatedAt().compareTo(b.getCreatedAt());
                })
                .toList();
    }

    /**
     * Adjusts the category of an order item, recording the reviewer who made the change.
     */
    public OrderItem adjustCategory(Long orderItemId, String newCategory, Long reviewerId) {
        OrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new EntityNotFoundException("OrderItem", orderItemId));
        item.setAdjustedCategory(newCategory);
        item.setAdjustedBy(reviewerId);
        item.setAdjustedAt(LocalDateTime.now());
        return orderItemRepository.save(item);
    }

    /**
     * Delegates contract initiation for a given order to ContractService.
     */
    public ContractInstance initiateContractForOrder(Long orderId, Long templateVersionId,
                                                     Long userId, Long reviewerId,
                                                     String fieldValues,
                                                     LocalDate startDate, LocalDate endDate) {
        return contractService.initiateContract(
                orderId, templateVersionId, userId, reviewerId, fieldValues, startDate, endDate);
    }
}
