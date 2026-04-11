package com.reclaim.portal.orders.service;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.service.AppointmentService;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.repository.RecyclingItemRepository;
import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.entity.OrderItem;
import com.reclaim.portal.orders.entity.OrderOperationLog;
import com.reclaim.portal.orders.repository.OrderItemRepository;
import com.reclaim.portal.orders.repository.OrderOperationLogRepository;
import com.reclaim.portal.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final String STATUS_PENDING = "PENDING_CONFIRMATION";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String STATUS_EXCEPTION = "EXCEPTION";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderOperationLogRepository orderOperationLogRepository;
    private final AppointmentService appointmentService;
    private final RecyclingItemRepository recyclingItemRepository;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        OrderOperationLogRepository orderOperationLogRepository,
                        AppointmentService appointmentService,
                        RecyclingItemRepository recyclingItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderOperationLogRepository = orderOperationLogRepository;
        this.appointmentService = appointmentService;
        this.recyclingItemRepository = recyclingItemRepository;
    }

    /**
     * Create a new order: validate appointment, book slot, snapshot items, calculate total.
     */
    public Order createOrder(Long userId, List<Long> itemIds, Long appointmentId, String appointmentType) {
        // Validate appointment exists and has capacity
        Appointment appointment = appointmentService.findById(appointmentId);
        if (!appointmentType.equals(appointment.getAppointmentType())) {
            throw new BusinessRuleException(
                "Appointment type mismatch: expected " + appointment.getAppointmentType()
                    + " but got " + appointmentType);
        }
        appointmentService.validateAppointmentTime(
            appointment.getAppointmentDate(), appointment.getStartTime());

        // Book the slot (throws if no capacity)
        appointmentService.bookSlot(appointmentId);

        // Create order
        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setUserId(userId);
        order.setAppointmentId(appointmentId);
        order.setOrderStatus(STATUS_PENDING);
        order.setAppointmentType(appointmentType);
        order.setRescheduleCount(0);
        order.setCurrency("USD");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = orderRepository.save(order);

        // Snapshot items
        List<RecyclingItem> items = recyclingItemRepository.findAllById(itemIds);
        if (items.isEmpty()) {
            throw new BusinessRuleException("No valid items found for the provided item ids");
        }

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        for (RecyclingItem item : items) {
            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getId());
            oi.setItemId(item.getId());
            oi.setSnapshotTitle(item.getTitle());
            oi.setSnapshotCategory(item.getCategory());
            oi.setSnapshotCondition(item.getItemCondition());
            oi.setSnapshotPrice(item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO);
            orderItems.add(oi);
            if (item.getPrice() != null) {
                total = total.add(item.getPrice());
            }
        }
        orderItemRepository.saveAll(orderItems);

        order.setTotalPrice(total);
        order = orderRepository.save(order);

        logOperation(order.getId(), userId, "ORDER_CREATED", null, STATUS_PENDING,
            "Order created with " + items.size() + " item(s)");
        log.info("Order created: id={}, userId={}, items={}", order.getId(), userId, items.size());

        return order;
    }

    /**
     * Accept a pending order: PENDING_CONFIRMATION -> ACCEPTED.
     */
    public Order acceptOrder(Long orderId, Long reviewerId) {
        Order order = findOrderById(orderId);
        requireStatus(order, STATUS_PENDING);

        String prev = order.getOrderStatus();
        order.setOrderStatus(STATUS_ACCEPTED);
        order.setReviewerId(reviewerId);
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        logOperation(orderId, reviewerId, "ORDER_ACCEPTED", prev, STATUS_ACCEPTED, null);
        return order;
    }

    /**
     * Complete an accepted order: ACCEPTED -> COMPLETED.
     */
    public Order completeOrder(Long orderId, Long actorId) {
        Order order = findOrderById(orderId);
        requireStatus(order, STATUS_ACCEPTED);

        String prev = order.getOrderStatus();
        order.setOrderStatus(STATUS_COMPLETED);
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        logOperation(orderId, actorId, "ORDER_COMPLETED", prev, STATUS_COMPLETED, null);
        return order;
    }

    /**
     * Cancel an order. If the appointment is less than 1 hour away, flag as EXCEPTION instead.
     * Only the order owner may request cancellation.
     */
    public Order cancelOrder(Long orderId, Long actorId, String reason) {
        Order order = findOrderById(orderId);
        if (actorId == null || !actorId.equals(order.getUserId())) {
            throw new BusinessRuleException("Access denied");
        }
        if (!STATUS_PENDING.equals(order.getOrderStatus())
                && !STATUS_ACCEPTED.equals(order.getOrderStatus())) {
            throw new BusinessRuleException(
                "Cannot cancel order in status: " + order.getOrderStatus());
        }

        // Determine if appointment is < 1 hr away
        Appointment appointment = appointmentService.findById(order.getAppointmentId());
        LocalTime startTime = LocalTime.parse(appointment.getStartTime(), TIME_FMT);
        LocalDateTime appointmentDateTime = appointment.getAppointmentDate().atTime(startTime);
        LocalDateTime now = LocalDateTime.now();
        boolean tooClose = appointmentDateTime.isBefore(now.plusHours(1));

        String prev = order.getOrderStatus();

        if (tooClose) {
            // Flag as exception - requires admin approval to proceed
            order.setOrderStatus(STATUS_EXCEPTION);
            order.setCancellationReason(reason);
            order.setUpdatedAt(now);
            order = orderRepository.save(order);
            logOperation(orderId, actorId, "CANCEL_REQUESTED", prev, STATUS_EXCEPTION,
                "Cancellation requested within 1 hour of appointment: " + reason);
        } else {
            order.setOrderStatus(STATUS_CANCELED);
            order.setCancellationReason(reason);
            order.setUpdatedAt(now);
            order = orderRepository.save(order);
            appointmentService.releaseSlot(order.getAppointmentId());
            logOperation(orderId, actorId, "ORDER_CANCELED", prev, STATUS_CANCELED, reason);
        }

        return order;
    }

    /**
     * Approve a cancellation that was flagged as EXCEPTION: EXCEPTION -> CANCELED.
     */
    public Order approveCancellation(Long orderId, Long reviewerId, String reason) {
        Order order = findOrderById(orderId);
        requireStatus(order, STATUS_EXCEPTION);

        String prev = order.getOrderStatus();
        order.setOrderStatus(STATUS_CANCELED);
        order.setCancellationApprovedBy(reviewerId);
        order.setCancellationReason(reason != null ? reason : order.getCancellationReason());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        appointmentService.releaseSlot(order.getAppointmentId());
        logOperation(orderId, reviewerId, "CANCEL_APPROVED", prev, STATUS_CANCELED,
            "Cancellation approved by reviewer " + reviewerId);

        return order;
    }

    /**
     * Reschedule an order to a new appointment slot.
     * Requires: PENDING or ACCEPTED, rescheduleCount < 2.
     * Only the order owner may reschedule.
     */
    public Order rescheduleOrder(Long orderId, Long actorId, Long newAppointmentId) {
        Order order = findOrderById(orderId);
        if (actorId == null || !actorId.equals(order.getUserId())) {
            throw new BusinessRuleException("Access denied");
        }
        if (!STATUS_PENDING.equals(order.getOrderStatus())
                && !STATUS_ACCEPTED.equals(order.getOrderStatus())) {
            throw new BusinessRuleException(
                "Cannot reschedule order in status: " + order.getOrderStatus());
        }
        if (order.getRescheduleCount() >= 2) {
            throw new BusinessRuleException("Order has already been rescheduled the maximum number of times (2)");
        }

        // Validate the new appointment
        Appointment newAppointment = appointmentService.findById(newAppointmentId);
        appointmentService.validateAppointmentTime(
            newAppointment.getAppointmentDate(), newAppointment.getStartTime());

        // Appointment type must match the original order type (cannot switch pickup/drop-off)
        if (order.getAppointmentType() != null
                && !order.getAppointmentType().equals(newAppointment.getAppointmentType())) {
            throw new BusinessRuleException(
                "Cannot change appointment type during reschedule: order is "
                    + order.getAppointmentType() + " but new slot is "
                    + newAppointment.getAppointmentType());
        }

        Long oldAppointmentId = order.getAppointmentId();

        // Release old slot and book new one
        appointmentService.releaseSlot(oldAppointmentId);
        appointmentService.bookSlot(newAppointmentId);

        String prev = order.getOrderStatus();
        order.setAppointmentId(newAppointmentId);
        order.setRescheduleCount(order.getRescheduleCount() + 1);
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        logOperation(orderId, actorId, "ORDER_RESCHEDULED", prev, order.getOrderStatus(),
            "Rescheduled from appointment " + oldAppointmentId + " to " + newAppointmentId);

        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByUser(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Order> getReviewerQueue() {
        return orderRepository.findByOrderStatus(STATUS_PENDING);
    }

    /**
     * Authorization helper: verifies that the actor is allowed to access an order.
     * Staff (isStaff=true) may always access. Otherwise, actorId must equal the order owner.
     * Also allows the assigned reviewer to view.
     */
    public void requireOrderOwnerOrStaff(Order order, Long actorId, boolean isStaff) {
        if (isStaff) {
            return;
        }
        if (actorId != null && actorId.equals(order.getUserId())) {
            return;
        }
        if (actorId != null && actorId.equals(order.getReviewerId())) {
            return;
        }
        throw new BusinessRuleException("Access denied");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOrderWithLogs(Long orderId, Long actorId, boolean isStaff) {
        Order order = findOrderById(orderId);
        if (!isStaff && (actorId == null || !actorId.equals(order.getUserId()))) {
            throw new BusinessRuleException("Access denied to order");
        }
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        List<OrderOperationLog> logs = orderOperationLogRepository
            .findByOrderIdOrderByCreatedAtAsc(orderId);
        return Map.of("order", order, "items", items, "logs", logs);
    }

    /** Backward-compatible variant used by internal/test callers — no ownership check. */
    @Transactional(readOnly = true)
    public Map<String, Object> getOrderWithLogs(Long orderId) {
        Order order = findOrderById(orderId);
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        List<OrderOperationLog> logs = orderOperationLogRepository
            .findByOrderIdOrderByCreatedAtAsc(orderId);
        return Map.of("order", order, "items", items, "logs", logs);
    }

    @Transactional(readOnly = true)
    public Order findOrderById(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
    }

    private void requireStatus(Order order, String expectedStatus) {
        if (!expectedStatus.equals(order.getOrderStatus())) {
            throw new BusinessRuleException(
                "Expected order status " + expectedStatus + " but found " + order.getOrderStatus());
        }
    }

    private void logOperation(Long orderId, Long actorId, String operation,
                               String previousStatus, String newStatus, String details) {
        OrderOperationLog log = new OrderOperationLog();
        log.setOrderId(orderId);
        log.setActorId(actorId);
        log.setOperation(operation);
        log.setPreviousStatus(previousStatus);
        log.setNewStatus(newStatus);
        log.setDetails(details);
        log.setCreatedAt(LocalDateTime.now());
        orderOperationLogRepository.save(log);
    }
}
