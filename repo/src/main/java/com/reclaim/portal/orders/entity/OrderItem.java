package com.reclaim.portal.orders.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "snapshot_title", length = 500)
    private String snapshotTitle;

    @Column(name = "snapshot_category", length = 100)
    private String snapshotCategory;

    @Column(name = "snapshot_condition", length = 50)
    private String snapshotCondition;

    @Column(name = "snapshot_price", precision = 10, scale = 2)
    private BigDecimal snapshotPrice;

    @Column(name = "adjusted_category", length = 100)
    private String adjustedCategory;

    @Column(name = "adjusted_by")
    private Long adjustedBy;

    @Column(name = "adjusted_at")
    private LocalDateTime adjustedAt;

    public OrderItem() {
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

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getSnapshotTitle() {
        return snapshotTitle;
    }

    public void setSnapshotTitle(String snapshotTitle) {
        this.snapshotTitle = snapshotTitle;
    }

    public String getSnapshotCategory() {
        return snapshotCategory;
    }

    public void setSnapshotCategory(String snapshotCategory) {
        this.snapshotCategory = snapshotCategory;
    }

    public String getSnapshotCondition() {
        return snapshotCondition;
    }

    public void setSnapshotCondition(String snapshotCondition) {
        this.snapshotCondition = snapshotCondition;
    }

    public BigDecimal getSnapshotPrice() {
        return snapshotPrice;
    }

    public void setSnapshotPrice(BigDecimal snapshotPrice) {
        this.snapshotPrice = snapshotPrice;
    }

    public String getAdjustedCategory() {
        return adjustedCategory;
    }

    public void setAdjustedCategory(String adjustedCategory) {
        this.adjustedCategory = adjustedCategory;
    }

    public Long getAdjustedBy() {
        return adjustedBy;
    }

    public void setAdjustedBy(Long adjustedBy) {
        this.adjustedBy = adjustedBy;
    }

    public LocalDateTime getAdjustedAt() {
        return adjustedAt;
    }

    public void setAdjustedAt(LocalDateTime adjustedAt) {
        this.adjustedAt = adjustedAt;
    }
}
