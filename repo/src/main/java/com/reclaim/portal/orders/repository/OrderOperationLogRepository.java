package com.reclaim.portal.orders.repository;

import com.reclaim.portal.orders.entity.OrderOperationLog;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Append-only repository for operation logs.
 * Extends the marker {@link Repository} interface instead of {@code JpaRepository}
 * to avoid exposing delete/update methods. The entity's {@code @PreUpdate} and
 * {@code @PreRemove} callbacks provide runtime enforcement as a second layer.
 */
public interface OrderOperationLogRepository extends Repository<OrderOperationLog, Long> {

    /** Persist a new operation log entry. */
    OrderOperationLog save(OrderOperationLog log);

    /** Retrieve all logs for an order in chronological order. */
    List<OrderOperationLog> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
