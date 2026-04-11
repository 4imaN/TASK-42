package com.reclaim.portal.orders.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Append-only audit log for order state transitions.
 *
 * <p>Immutability is enforced at two layers:
 * <ul>
 *   <li><b>Application layer</b> — {@code @PreUpdate} and {@code @PreRemove} JPA callbacks
 *       reject any mutation attempt. The repository interface exposes only
 *       {@code save()} (for inserts) and read methods.</li>
 *   <li><b>Database layer</b> — MySQL triggers ({@code prevent_oplog_update},
 *       {@code prevent_oplog_delete}) in migration {@code V9} block UPDATE/DELETE
 *       at the DB level. H2 (test profile) does not support SIGNAL triggers, so
 *       the test suite validates immutability through the JPA callbacks only.</li>
 * </ul>
 */
@Entity
@Table(name = "order_operation_logs")
public class OrderOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** JPA lifecycle guard — blocks Hibernate-level updates. */
    @PreUpdate
    private void preventUpdate() {
        throw new UnsupportedOperationException("Operation logs are immutable and cannot be modified");
    }

    /** JPA lifecycle guard — blocks Hibernate-level deletes. */
    @PreRemove
    private void preventRemove() {
        throw new UnsupportedOperationException("Operation logs are immutable and cannot be deleted");
    }

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "operation", length = 50)
    private String operation;

    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "new_status", length = 30)
    private String newStatus;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public OrderOperationLog() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
